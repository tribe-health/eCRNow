package com.drajer.sof.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent;
import org.hl7.fhir.r4.model.Encounter.EncounterParticipantComponent;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.drajer.eca.model.ActionRepo;
import com.drajer.sof.model.LaunchDetails;
import com.drajer.sof.model.R4FhirData;
import com.drajer.sof.utils.FhirContextInitializer;
import com.drajer.sof.utils.R4ResourcesData;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;

@Component
public class TriggerQueryR4Bundle {

	@Autowired
	FhirContextInitializer fhirContextInitializer;

	@Autowired
	R4ResourcesData r4ResourcesData;

	private static final Logger logger = LoggerFactory.getLogger(TriggerQueryR4Bundle.class);

	public Bundle createR4Bundle(LaunchDetails launchDetails, R4FhirData r4FhirData, Date start, Date end) {
		Bundle bundle = new Bundle();

		logger.info("Initializing FHIR Context for Version::::" + launchDetails.getFhirVersion());
		FhirContext context = fhirContextInitializer.getFhirContext(launchDetails.getFhirVersion());
		logger.info("Initializing Client");
		IGenericClient client = fhirContextInitializer.createClient(context, launchDetails.getEhrServerURL(),
				launchDetails.getAccessToken());

		// GET Patient Details and Add to Bundle
		try {
			logger.info("Get Patient Data");
			Patient patient = (Patient) fhirContextInitializer.getResouceById(launchDetails, client, context, "Patient",
					launchDetails.getLaunchPatientId());
			BundleEntryComponent patientEntry = new BundleEntryComponent();
			patientEntry.setResource(patient);
			bundle.addEntry(patientEntry);
		} catch (Exception e) {
			logger.error("Error in getting Patient Data");
		}
		// Step 1: Get Encounters for Patient based on encId. (Create a method to get
		// encounters)
		// If encId is null, find encounters for patient within the start and end time
		// provided.
		// Add to the bundle.
		// As you are adding to the bundle within Fhir Data, add the codeable concept
		// also to the list of encounterCodes.
		Encounter encounter = null;
		try {
			logger.info("Get Encounter Data");
			encounter = r4ResourcesData.getEncounterData(context, client, launchDetails, r4FhirData, start, end);
			if (encounter.getParticipant() != null) {
				List<EncounterParticipantComponent> participants = encounter.getParticipant();
				for (EncounterParticipantComponent participant : participants) {
					if (participant.getIndividual() != null) {
						Reference practitionerReference = participant.getIndividual();
						Practitioner practitioner = (Practitioner) fhirContextInitializer.getResouceById(launchDetails,
								client, context, "Practitioner",
								practitionerReference.getReferenceElement().getIdPart());
						BundleEntryComponent practitionerEntry = new BundleEntryComponent().setResource(practitioner);
						bundle.addEntry(practitionerEntry);
					}
				}
			}
			if (encounter.getServiceProvider() != null) {
				Reference organizationReference = encounter.getServiceProvider();
				Organization organization = (Organization) fhirContextInitializer.getResouceById(launchDetails, client,
						context, "Organization", organizationReference.getReferenceElement().getIdPart());
				BundleEntryComponent organizationEntry = new BundleEntryComponent().setResource(organization);
				bundle.addEntry(organizationEntry);
			}
			if (encounter.getLocation() != null) {
				List<EncounterLocationComponent> enocunterLocations = encounter.getLocation();
				for (EncounterLocationComponent location : enocunterLocations) {
					if (location.getLocation() != null) {
						Reference locationReference = location.getLocation();
						Location locationResource = (Location) fhirContextInitializer.getResouceById(launchDetails,
								client, context, "Location", locationReference.getReferenceElement().getIdPart());
						BundleEntryComponent locationEntry = new BundleEntryComponent().setResource(locationResource);
						bundle.addEntry(locationEntry);
					}
				}
			}
			BundleEntryComponent encounterEntry = new BundleEntryComponent().setResource(encounter);
			bundle.addEntry(encounterEntry);
		} catch (Exception e) {
			logger.error("Error in getting Encounter Data");
		}

		// Step 2: Get Conditions for Patient (Write a method)
		// Filter the conditions based on encounter Reference if Encounter Reference is
		// present.
		// If encounter is not present, then filter based on times (Start and end, if
		// Condition time is between start and end times) -- Do this later.
		// Add to the bundle
		// As you are adding to the bundle within Fhir Data, add the codeable concept
		// also to the list of ConditionCodes.
		try {
			logger.info("Get Condition Data");
			List<Condition> conditionsList = r4ResourcesData.getConditionData(context, client, launchDetails,
					r4FhirData, encounter, start, end);
			logger.info("Filtered ConditionsList---->" + conditionsList.size());
			r4FhirData.setConditions(conditionsList);
			for (Condition condition : conditionsList) {
				BundleEntryComponent conditionsEntry = new BundleEntryComponent().setResource(condition);
				bundle.addEntry(conditionsEntry);
			}
		} catch (Exception e) {
			logger.error("Error in getting Condition Data");
		}

		// Get Observations for Patients and laboratory category (Write a method).
		// Filter the observations based on encounter Reference if encounter is present.
		// If encounter is not present, then filter based on times (Start and end, if
		// observation time is between start and end times) -- Do this later.
		// Add to the bundle
		// As you are adding to the bundle within Fhir Data, add the codeable concept
		// also to the list of labResultCodes.
		try {
			logger.info("Get Observation Data");
			List<Observation> observationList = r4ResourcesData.getObservationData(context, client, launchDetails,
					r4FhirData, encounter, start, end);
			logger.info("Filtered Observations---->" + observationList.size());
			r4FhirData.setLabResults(observationList);
			for (Observation observation : observationList) {
				BundleEntryComponent observationsEntry = new BundleEntryComponent().setResource(observation);
				bundle.addEntry(observationsEntry);
			}
		} catch (Exception e) {
			logger.error("Error in getting Observation Data");
		}

		// Get MedicationAdministration for Patients and laboratory category (Write a
		// method).
		// Filter the MedicationAdministrations based on encounter Reference if
		// encounter is present.
		// If encounter is not present, then filter based on times (Start and end, if
		// medicationadministration time is between start and end times) -- Do this
		// later.
		// Add to the bundle
		// As you are adding to the bundle within Fhir Data, add the codeable concept
		// also to the list of medicationCodes.
		try {
			logger.info("Get MedicationAdministration Data");
			List<MedicationAdministration> medAdministrationsList = r4ResourcesData
					.getMedicationAdministrationData(context, client, launchDetails, r4FhirData, encounter, start, end);
			logger.info("Filtered MedicationAdministration----------->" + medAdministrationsList.size());
			r4FhirData.setMedicationAdministrations(medAdministrationsList);
			for (MedicationAdministration medAdministration : medAdministrationsList) {
				if(!medAdministration.getMedication().isEmpty() && medAdministration.getMedication() != null) {
					if(medAdministration.getMedication() instanceof Reference) {
						Reference medRef = (Reference) medAdministration.getMedication();
						String medReference = medRef.getReferenceElement().getValue();
						if(medReference.startsWith("#")) {
							List<Resource> medAdministrationContained = medAdministration.getContained();
							// List<Medication> containedResources = medAdministrationContained;
							if(medAdministrationContained.stream().anyMatch(resource -> resource.getIdElement().getValue().equals(medReference))) {
								logger.info("Medication Resource exists in MedicationAdministration.contained. So no need to add again in Bundle.");
							}
						} else {
							logger.info("Medication Reference Found=============>"+medReference);
							Medication medication = r4ResourcesData.getMedicationData(context, client, launchDetails, r4FhirData, medReference);
							BundleEntryComponent medicationEntry = new BundleEntryComponent().setResource(medication);
							bundle.addEntry(medicationEntry);
							if(medication != null) {
								List<Medication> medicationList = new ArrayList<Medication>();
								medicationList.add(medication);
								r4FhirData.setMedicationList(medicationList);	
							}
						}
					}
				}
				BundleEntryComponent medAdministrationEntry = new BundleEntryComponent().setResource(medAdministration);
				bundle.addEntry(medAdministrationEntry);
			}
		} catch (Exception e) {
			logger.error("Error in getting the MedicationAdministration Data", e);
		}

		// Get ServiceRequest for Patients (Write a method).
		// Filter the ServiceReques based on encounter Reference if encounter is
		// present.
		// If encounter is not present, then filter based on times (Start and end, if
		// ServiceRequest time is between start and end times) -- Do this later.
		// Add to the bundle
		// As you are adding to the bundle within Fhir Data, add the codeable concept
		// also to the list of ServiceRequestCodes.

		try {
			logger.info("Get ServiceRequest Data");
			List<ServiceRequest> serviceRequestsList = r4ResourcesData.getServiceRequestData(context, client,
					launchDetails, r4FhirData, encounter, start, end);
			logger.info("Filtered ServiceRequests----------->" + serviceRequestsList.size());
			r4FhirData.setServiceRequests(serviceRequestsList);
			for (ServiceRequest serviceRequest : serviceRequestsList) {
				BundleEntryComponent serviceRequestEntry = new BundleEntryComponent().setResource(serviceRequest);
				bundle.addEntry(serviceRequestEntry);
			}
		} catch (Exception e) {
			logger.error("Error in getting the ServiceRequest Data");
		}

		// Setting bundle to FHIR Data
		logger.info("------------------------------CodeableConcept Codes------------------------------");
		logger.info("Encounter Codes Size=====>" + r4FhirData.getR4EncounterCodes().size());
		logger.info("Conditions Codes Size=====>" + r4FhirData.getR4ConditionCodes().size());
		logger.info("Observation Codes Size=====>" + r4FhirData.getR4LabResultCodes().size());
		logger.info("Medication Codes Size=====>" + r4FhirData.getR4MedicationCodes().size());
		logger.info("ServiceRequests Codes Size=====>" + r4FhirData.getR4ServiceRequestCodes().size());

		// logger.info(context.newJsonParser().encodeResourceToString(bundle));
		
		String fileName = ActionRepo.getInstance().getLogFileDirectory()+"/TriggerQueryR4Bundle-"+launchDetails.getLaunchPatientId()+".json";
		FhirContextInitializer.saveBundleToFile(context.newJsonParser().encodeResourceToString(bundle), fileName);
		return bundle;
	}

}
