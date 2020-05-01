package com.drajer.eca.model;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.PlanDefinition.ActionRelationshipType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import com.drajer.cda.CdaEicrGenerator;
import com.drajer.eca.model.EventTypes.JobStatus;
import com.drajer.sof.model.Dstu2FhirData;
import com.drajer.sof.model.FhirData;
import com.drajer.sof.model.LaunchDetails;
import com.drajer.sof.service.LaunchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CreateEicrAction extends AbstractAction {

	private final Logger logger = LoggerFactory.getLogger(CreateEicrAction.class);
	
	public CreateEicrAction() {
		
		// taskScheduler = new ThreadPoolTaskScheduler();
	}

	@Override
	public void print() {

		logger.info(" **** Printing CreateEicrAction **** ");
		printBase();
		logger.info(" **** End Printing CreateEicrAction **** ");
	}

	@SuppressWarnings("deprecation")
	@Override
	public void execute(Object obj) {

		logger.info(" Executing Match Trigger Action ");

		if (obj instanceof LaunchDetails) {

			logger.info(" Obtained Launch Details ");
			LaunchDetails details = (LaunchDetails) obj;
			ObjectMapper mapper = new ObjectMapper();
			PatientExecutionState state = null;

			try {
				state = mapper.readValue(details.getStatus(), PatientExecutionState.class);
				state.getCreateEicrStatus().setActionId(getActionId());
			} catch (JsonMappingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (JsonProcessingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			logger.info(" Create Eicr State " + details.getStatus());
			
			DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
			Date start = null;
			Date end = null;

			try {
				start = formatter.parse("2019-02-13");
				end = formatter.parse("2019-02-14");

			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// Handle Conditions
			Boolean conditionsMet = true;
			if (getPreConditions() != null && getPreConditions().size() > 0) {

				logger.info(" Checking on PreConditions ");
				List<AbstractCondition> conds = getPreConditions();

				for (AbstractCondition cond : conds) {

					if (!cond.evaluate(details)) {
						logger.info(" Condition Not met " + cond.getConditionType().toString());
						conditionsMet = false;
					}
				}
			}

			// PreConditions Met, then process related actions.
			Boolean relatedActsDone = true;
			if (conditionsMet) {
				
				logger.info(" PreConditions have been Met ");

				if (getRelatedActions() != null && getRelatedActions().size() > 0) {

					List<RelatedAction> racts = getRelatedActions();

					for (RelatedAction ract : racts) {

						if (ract.getRelationship() == ActionRelationshipType.AFTER) {

							// check if the action is completed.
							String actionId = ract.getRelatedAction().getActionId();

							if (!state.hasActionCompleted(actionId)) {

								logger.info(
										" Action " + actionId + " is not completed , hence this action has to wait ");
								relatedActsDone = false;
							}
						}
					}
				}
			}

			// Check Timing Data , Dont check if the state is already scheduled meaning the
			// job was scheduled already.
			if (relatedActsDone) {

				if (state.getCreateEicrStatus().getJobStatus() == JobStatus.NOT_STARTED) {
					
					logger.info(" Related Actions Done and this job has not started ");
					if (getTimingData() != null && getTimingData().size() > 0) {
						
						logger.info(" Timing Data is present ");
						List<TimingSchedule> tsjobs = getTimingData();

						for (TimingSchedule ts : tsjobs) {

							// TBD : Setup job using TS Timing after testing so that we can test faster.
							// For now setup a default job with 10 seconds.
							try {
								
								scheduleJob(details.getId(), ts);
								state.getCreateEicrStatus().setJobStatus(JobStatus.SCHEDULED);
								details.setStatus(mapper.writeValueAsString(state));
								
								return;
							} catch (JsonProcessingException e) { // TODO Auto-generated catch block
								e.printStackTrace();
							}

							
						}

					}
				}

			}

			// Do not expect the trigger data to be present for CreateEicr
			// Call the Loading Queries and create eICR.
			if (ActionRepo.getInstance().getLoadingQueryService() != null) {

				logger.info(" Getting necessary data from Loading Queries ");
				FhirData data = ActionRepo.getInstance().getLoadingQueryService().getData(details, start, end);

				String eICR = null;

				if (data != null && data instanceof Dstu2FhirData) {

					Dstu2FhirData dstu2Data = (Dstu2FhirData) data;
					eICR = CdaEicrGenerator.convertDstu2FhirBundletoCdaEicr(dstu2Data, details);

					state.getCreateEicrStatus().setJobStatus(JobStatus.COMPLETED);
					try {
						details.setStatus(mapper.writeValueAsString(state));
					} catch (JsonProcessingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}

				logger.info(" **** Printing Eicr **** ");

				logger.info(eICR);
				

				logger.info(" **** End Printing Eicr **** ");
			}

		}
	}

	class ExecuteJob implements Runnable {

		public Integer launchDetailsId;

		public ExecuteJob(Integer launchDetailsId) {
			this.launchDetailsId = launchDetailsId;
		}

		@Override
		public void run() {
			try {
				logger.info("Get Launch Details from Database using Id==============>" + launchDetailsId);
				LaunchDetails launchDetails = ActionRepo.getInstance().getLaunchService().getAuthDetailsById(launchDetailsId);
				execute(launchDetails);
				logger.info("Starting the Thread");
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				logger.info("Error in Getting Data=====>" + e.getMessage());
			}
		}
	}

	public void scheduleJob(Integer launchDetailsId, TimingSchedule ts) {
		logger.info("Scheduling Job to Get Data from FHIR Server");
		ActionRepo.getInstance().getTaskScheduler().schedule(new ExecuteJob(launchDetailsId), new Date().toInstant().plusSeconds(10));
		logger.info("Job Scheduled to get Data after 10 seconds");
	}

}
