package com.drajer.cda.utils;

import com.drajer.eca.model.ActionRepo;
import com.helger.schematron.ISchematronResource;
import com.helger.schematron.svrl.jaxb.FailedAssert;
import com.helger.schematron.svrl.jaxb.SchematronOutputType;
import com.helger.schematron.xslt.SchematronResourceSCH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * Util class for validating Cda data.
 */

public class CdaValidatorUtil {

    public static final Logger logger = LoggerFactory.getLogger(CdaValidatorUtil.class);

    /**
     * Method validates XML data against xsdFile
     *
     * @param xmlData
     * @param xsdFilePath
     * @return boolean value
     */

    public static boolean validateEicrXMLData(String xmlData, String xsdFilePath){
        try {
            logger.info(" **** Starting XML validation from xsd **** ");
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = schemaFactory.newSchema(new File(xsdFilePath));

            Validator validator = schema.newValidator();
            // Add a custom ErrorHandler
            ValidateErrorHandler errorHandler = new ValidateErrorHandler();
            validator.setErrorHandler(errorHandler);
            validator.validate(new StreamSource(new ByteArrayInputStream(xmlData.getBytes())));

            logger.info(" **** End of XML validation from xsd **** ");

            if(errorHandler.isException) return false;

        } catch (SAXException | IOException e) {
            logger.error("Message: Error validating XML Data " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }


    /**
     * Method used for validating XML data against valid Schematron
     * returns true if XML data matched Schematton
     * @param ecrData
     * @return boolean value
     */

    public static boolean validateEicrToSchematron(String ecrData) {
        boolean validationResult = false;
        final ISchematronResource aResSCH = SchematronResourceSCH.fromFile(ActionRepo.getInstance().getSchematronFileLocation());

        if (!aResSCH.isValidSchematron ()) {
            logger.info(" *** Cannot Validate since Schematron is not valid *** ");
        }
        else {
            SchematronOutputType output = null;
            try {
                logger.info("Found Valid Schematron which can be applied EICR ");
                output = aResSCH.applySchematronValidationToSVRL(new StreamSource(new StringReader(ecrData)));
            } catch (Exception e) {
                logger.error("Unable to read/write execution state: "+e.getMessage() );
                e.printStackTrace();
            }

            if(output != null) {
                List<Object> objs = output.getActivePatternAndFiredRuleAndFailedAssert();
                boolean foundFailures = false;
                logger.info(" Number of Failed Assertions " + objs.size());

                for(Object obj : objs) {
                    if(obj instanceof FailedAssert) {
                        FailedAssert fa = (FailedAssert)obj;
                        if(fa.getFlag() != null && (fa.getFlag().contentEquals("error"))) {
                            foundFailures = true;
                            logger.info(" Failed Asertion : Id = " + fa.getId() + " , Location = " + fa.getLocation()
                                    + " , Text = " + fa.getText() + ", Flag = " + fa.getFlag());
                        }else {
                            // It is a warning, so need to print to log for analysis
                            //logger.info("Failed Asertion : Id = " + fa.getId() + ", Flag = " + fa.getFlag());
                        }
                    }
                }

                if(foundFailures)
                    validationResult = false;
                else
                    validationResult = true;
            }
            else {
                logger.info("Schematron Validation Ouput is null, so validation was not performed ");
                validationResult = false;
            }
        }
        return validationResult;
    }

}
