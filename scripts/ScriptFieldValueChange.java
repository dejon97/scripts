import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.ephesoft.dcma.util.XMLUtil;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.XMLOutputter;
import com.ephesoft.dcma.util.logger.EphesoftLogger;
import com.ephesoft.dcma.util.logger.ScriptLoggerFactory;

import com.ephesoft.dcma.core.component.ICommonConstants;
import com.ephesoft.dcma.util.ApplicationConfigProperties;

import com.ephesoft.dcma.script.IJDomScript;

import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.HttpURLConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ScriptFieldValueChange implements IJDomScript {

	private static EphesoftLogger LOGGER = ScriptLoggerFactory.getLogger(ScriptFieldValueChange.class);
	
	private static String BATCH_LOCAL_PATH = "BatchLocalPath";
	private static String BATCH_INSTANCE_ID = "BatchInstanceIdentifier";
	private static String ZIP_FILE_EXT = ".zip";
	private static String EXT_BATCH_XML_FILE = "_batch.xml";
	
	private static String VALIDATE_PROVIDER_HOST = "";
	private static String VALIDATE_MEMBER_HOST = "";
	
	public Object execute(Document document, String fieldName, String docIdentifier) {
		Exception exception = null;
		
		try {
			if (document == null) {
				LOGGER.error("Input document is null.");
				throw new Exception("Input document is null.");
			} else {
				init(document);
				changeField(document, fieldName, docIdentifier);
			}
			
		} catch (Exception e) {
			LOGGER.error("*************  Error occurred in scripts." + e.getMessage());
			exception = e;
		}
		
		return exception;
	}

	public void changeField(Document document, String fieldName, String docIdentifier) throws Exception {
		LOGGER.info("*************  Inside field value change script.");

		LOGGER.info("*************  Start execution of field value change  script.");			
		Boolean saveChanges = false;
				
		Element documents = document.getRootElement().getChild("Documents");
		List<Element> documentsList = documents.getChildren();

		for (Element item: documentsList) {
			if (docIdentifier.equals(item.getChildText("Identifier"))) {
				String fieldValue = getFieldValue(item, fieldName);
				
				String confidenceValue = "";
				
				if (isNPIField(fieldName)) {
					confidenceValue = isValidNPI(fieldValue) ? "100" : "0";
					
					LOGGER.error("*************  fieldName " + fieldName + " fieldValue " + fieldValue + " confidenceValue " + confidenceValue);
					
					if (confidenceValue == "0") {
						setFieldValue(item, fieldName, "Invalid NPI");
					}
										
					saveChanges     = true;
				//} else if (isMemberField(fieldName)) {
				//	confidenceValue = isValidMember(fieldValue) ? "100" : "0";
					
				//	LOGGER.error("*************  fieldName " + fieldName + " fieldValue " + fieldValue + " confidenceValue " + confidenceValue);
					
				//	saveChanges     = true;
				//}
				
				break;
				}
			}
		}
			
		//dumpValues(document, docIdentifier);
		
		if (saveChanges) {
			writeToXML(document);
		}
		
		LOGGER.info("*************  End execution of the Field Value Change script.");
	}

	/**
	 * The <code>writeToXML</code> method will write the state document to the XML file.
	 * 
	 * @param document {@link Document}.
	 */
	private void writeToXML(Document document) {

		String batchLocalPath = null;
		List<?> batchLocalPathList = document.getRootElement().getChildren(BATCH_LOCAL_PATH);
		if (null != batchLocalPathList) {
			batchLocalPath = ((Element) batchLocalPathList.get(0)).getText();
		}

		if (null == batchLocalPath) {
			LOGGER.error("Unable to find the local folder path in batch xml file.");
			return;
		}

		String batchInstanceID = null;
		List<?> batchInstanceIDList = document.getRootElement().getChildren(BATCH_INSTANCE_ID);
		if (null != batchInstanceIDList) {
			batchInstanceID = ((Element) batchInstanceIDList.get(0)).getText();

		}

		if (null == batchInstanceID) {
			LOGGER.error("Unable to find the batch instance ID in batch xml file.");
			return;
		}

		String batchXMLPath = batchLocalPath.trim() + File.separator + batchInstanceID + File.separator + batchInstanceID
				+ EXT_BATCH_XML_FILE;

		boolean isZipSwitchOn = true;
		try {
			ApplicationConfigProperties prop = ApplicationConfigProperties.getApplicationConfigProperties();
			isZipSwitchOn = Boolean.parseBoolean(prop.getProperty(ICommonConstants.ZIP_SWITCH));
		} catch (IOException ioe) {
			LOGGER.error("Unable to read the zip switch value. Taking default value as true. Exception thrown is:" + ioe.getMessage(),
					ioe);
		}

		LOGGER.info("isZipSwitchOn************" + isZipSwitchOn);
		OutputStream outputStream = null;
		FileWriter writer = null;
		XMLOutputter out = new com.ephesoft.dcma.batch.encryption.util.BatchInstanceXmlOutputter(batchInstanceID);

		try {
			if (isZipSwitchOn) {
				LOGGER.info("Found the batch xml zip file.");
				outputStream = getOutputStreamFromZip(batchXMLPath, batchInstanceID + EXT_BATCH_XML_FILE);
				out.output(document, outputStream);
			} else {
				writer = new java.io.FileWriter(batchXMLPath);
				out.output(document, writer);
				writer.flush();
				writer.close();
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		} finally {
			if (outputStream != null) {
				try {
					outputStream.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public static OutputStream getOutputStreamFromZip(final String zipName, final String fileName) throws FileNotFoundException,
			IOException {
		ZipOutputStream stream = null;
		stream = new ZipOutputStream(new FileOutputStream(new File(zipName + ZIP_FILE_EXT)));
		ZipEntry zipEntry = new ZipEntry(fileName);
		stream.putNextEntry(zipEntry);
		return stream;
	}
	

	private static String getFieldValue(Element doc, String field_name) {
		
		String retValue = null;
						
		Element documentLevelFields = doc.getChild("DocumentLevelFields");
		List<Element> documentLevelFieldList = documentLevelFields.getChildren("DocumentLevelField");
			
		for (int idx = 0; idx < documentLevelFieldList.size(); idx++) {				
			if (field_name.equals(documentLevelFieldList.get(idx).getChildText("Name"))) {
				retValue = documentLevelFieldList.get(idx).getChildText("Value");
				break;
			}
		}
		
		return retValue;
	}
	
	
	private static void setFieldValue(Element doc, String field_name, String field_value) {
		
		Element documentLevelFields = doc.getChild("DocumentLevelFields");
		List<Element> documentLevelFieldList = documentLevelFields.getChildren("DocumentLevelField");
		
		for (int idx = 0; idx < documentLevelFieldList.size(); idx++) {				
			if (field_name.equals(documentLevelFieldList.get(idx).getChildText("Name"))) {
				
				if (documentLevelFieldList.get(idx).getChild("Value") != null)
					documentLevelFieldList.get(idx).getChild("Value").setText(field_value);
				else {
					documentLevelFieldList.get(idx).addContent(new Element("Value").setText(field_value));
				}
				
				break;
			}
		}	
	}
	
	private static void dumpValues(Document doc, String docIdentifier) {
		
		Element documents = doc.getRootElement().getChild("Documents");
		List<Element> documentsList = documents.getChildren();
				
		String docLevelFieldName = "";
		String docLevelFieldValue = "";
		String docLevelConfidence = "";
		
		for (Element document: documentsList) {	
			if (docIdentifier.equals(document.getChildText("Identifier"))) {
			
				Element documentLevelFields = document.getChild("DocumentLevelFields");
				
				if (documentLevelFields != null) {
					List<Element> documentLevelFieldList = documentLevelFields.getChildren("DocumentLevelField");
					
					for (Element documentLevelField: documentLevelFieldList) {
						docLevelFieldName = documentLevelField.getChildText("Name");
						docLevelFieldValue = documentLevelField.getChildText("Value");											
						docLevelConfidence = documentLevelField.getChildText("Confidence");

						LOGGER.error("*************  docLevelFieldName " + docLevelFieldName + " docLevelFieldValue " + docLevelFieldValue + " docLevelConfidence " + docLevelConfidence);
					}
				}
				
				break;
			}
		}
	}
	
	
	private boolean isValidNPI(String npi) throws Exception {
		
		
		if (npi == null || npi.length() != 10) {
		return false;
		}
		
		String responseString = "";
		String outputString = "";
		
		String wsURL = String.format("http://%s/ProviderMemberValidationService.svc", VALIDATE_PROVIDER_HOST);
		
		URL url = new URL(wsURL);
		URLConnection connection = url.openConnection();
		HttpURLConnection httpConn = (HttpURLConnection) connection;
		
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		
		String xmlInput =		
			" <soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:opss=\"http://opss.services.org\">\n" +
			" <soapenv:Header/>\n" +
			" <soapenv:Body>\n" +
			" <opss:ValidateProvider>\n" +
			" <!--Optional:-->\n" +
			" <opss:providerId>" + npi + "</opss:providerId>\n" +
			" </opss:ValidateProvider>\n" +
			" </soapenv:Body>\n" +
			" </soapenv:Envelope>";
		 
		byte[] buffer = new byte[xmlInput.length()];
		buffer = xmlInput.getBytes();
		bout.write(buffer);
		
		byte[] b = bout.toByteArray();
		
		String SOAPAction = "http://opss.services.org/IProviderMemberValidationService/ValidateProvider";
		
		httpConn.setRequestProperty("Content-Length", String.valueOf(b.length));
		httpConn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
		httpConn.setRequestProperty("SOAPAction", SOAPAction);
		httpConn.setRequestMethod("POST");
		
		httpConn.setDoOutput(true);
		httpConn.setDoInput(true);
		
		OutputStream out = httpConn.getOutputStream();
		out.write(b);
		out.close();
		 
		InputStreamReader isr = new InputStreamReader(httpConn.getInputStream());
		BufferedReader in = new BufferedReader(isr);
		 
		while ((responseString = in.readLine()) != null) {
			outputString = outputString + responseString;
		}
		
//		outputString = "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><ValidateProviderResponse xmlns=\"http://opss.services.org\"><ValidateProviderResult>false</ValidateProviderResult></ValidateProviderResponse></s:Body></s:Envelope>";
		
		System.out.println(outputString);
		
		InputStream responseStream = new ByteArrayInputStream(outputString.getBytes());
		Document responseDocument = XMLUtil.createJDOMDocumentFromInputStream(responseStream);
		
		Element root = responseDocument.getRootElement();
		Element bodyItem = root.getChild("Body", Namespace.getNamespace("http://schemas.xmlsoap.org/soap/envelope/"));
		Element providerResponse = bodyItem.getChild("ValidateProviderResponse", Namespace.getNamespace("http://opss.services.org"));
		Element providerResult = providerResponse.getChild("ValidateProviderResult", Namespace.getNamespace("http://opss.services.org"));
		 			
		return Boolean.getBoolean(providerResult.getValue());
	}

	private boolean isValidMember(String memberId) throws Exception {
		
		
		if (memberId == null || memberId.length() != 12) {
		return false;
		}
		
		String responseString = "";
		String outputString = "";
		
		String wsURL = String.format("http://%s/ProviderMemberValidationService.svc", VALIDATE_MEMBER_HOST);
		
		URL url = new URL(wsURL);
		URLConnection connection = url.openConnection();
		HttpURLConnection httpConn = (HttpURLConnection) connection;
		
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		
		String xmlInput =		
			" <soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:opss=\"http://opss.services.org\">\n" +
			" <soapenv:Header/>\n" +
			" <soapenv:Body>\n" +
			" <opss:ValidateMember>\n" +
			" <!--Optional:-->\n" +
			" <opss:memberId>" + memberId + "</opss:memberId>\n" +
			" </opss:ValidateMember>\n" +
			" </soapenv:Body>\n" +
			" </soapenv:Envelope>";
		 
		byte[] buffer = new byte[xmlInput.length()];
		buffer = xmlInput.getBytes();
		bout.write(buffer);
		
		byte[] b = bout.toByteArray();
		
		String SOAPAction = "http://opss.services.org/IProviderMemberValidationService/ValidateMember";
		
		httpConn.setRequestProperty("Content-Length", String.valueOf(b.length));
		httpConn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
		httpConn.setRequestProperty("SOAPAction", SOAPAction);
		httpConn.setRequestMethod("POST");
		
		httpConn.setDoOutput(true);
		httpConn.setDoInput(true);
		
		OutputStream out = httpConn.getOutputStream();
		out.write(b);
		out.close();
		 
		InputStreamReader isr = new InputStreamReader(httpConn.getInputStream());
		BufferedReader in = new BufferedReader(isr);
		 
		while ((responseString = in.readLine()) != null) {
			outputString = outputString + responseString;
		}
		
//		outputString = "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><ValidateMemberResponse xmlns=\"http://opss.services.org\"><ValidateMemberResult>false</ValidateMemberResult></ValidateMemberResponse></s:Body></s:Envelope>";
		
		System.out.println(outputString);
		
		InputStream responseStream = new ByteArrayInputStream(outputString.getBytes());
		Document responseDocument = XMLUtil.createJDOMDocumentFromInputStream(responseStream);
		
		Element root = responseDocument.getRootElement();
		Element bodyItem = root.getChild("Body", Namespace.getNamespace("http://schemas.xmlsoap.org/soap/envelope/"));
		Element providerResponse = bodyItem.getChild("ValidateMemberResponse", Namespace.getNamespace("http://opss.services.org"));
		Element providerResult = providerResponse.getChild("ValidateMemberResult", Namespace.getNamespace("http://opss.services.org"));
		 			
		return Boolean.getBoolean(providerResult.getValue());
	}
	
	private void init(Document document) throws Exception {
		
		Properties properties = getProperties(document, "ValidationConfig.properties");
		
		VALIDATE_PROVIDER_HOST = properties.getProperty("VALIDATE_PROVIDER_HOST");
		VALIDATE_MEMBER_HOST = properties.getProperty("VALIDATE_MEMBER_HOST");
	}
	
	private Properties getProperties(Document document, String propertyFileName) throws Exception {
		
		Properties props = new Properties();
		InputStream input = null; 
		
		try {			
			String configFileLocaton = getConfigurationFileLocation(document);
			
			Path propFileLocation = Paths.get(configFileLocaton, propertyFileName); 
			
//			Path propFileLocation = Paths.get(".","scripts", "OCRSolutions-Accenture", propertyFileName);
			
			input = new FileInputStream(propFileLocation.toString());
			
			props.load(input);
			
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					throw e;
				}
			}			
		}
		
		return props;
	}
	
	private String getConfigurationFileLocation(Document document) {
		Element root = document.getRootElement();
		
		String batchLocalPath       = root.getChildText("BatchLocalPath");
		String batchClassIdentifier = root.getChildText("BatchClassIdentifier");
		
		Path configLocation = Paths.get(batchLocalPath.replaceFirst("ephesoft-system-folder", ""), batchClassIdentifier, "script-config");
		
		return configLocation.toString();		
	}
	
	private static boolean isNPIField(String fieldName) {
		
		boolean retValue = false;
		
		switch(fieldName) {
			case "HCFA32aNPI":
			case "HCFA32b":
			case "HCFA33aproviderNPI":
			case "HCFA33bprovider":
			case "HCFA17aReferringProviderID":
			case "HCFA17bReferringProviderNPI":
			case "HCFA24JNPI1":
			case "HCFA24JNPI2":
			case "HCFA24JNPI3":
			case "HCFA24JNPI4":
			case "HCFA24JNPI5":
			case "HCFA24JNPI6":
			case "UB56npi":
			case "UB76anpi":
			case "UB77anpi":
			case "UB78bnpi":
			case "UB79bnpi":
				retValue = true;
				break;
		}
		
		return retValue;
	}
	
	private static boolean isMemberField(String fieldName) {
		
		boolean retValue = false;
		
		switch(fieldName) {
			case "HCFA01A":
			case "HCFA26PatientsacctNum":
			case "UB8apatientID":
				retValue = true;
				break;
		}
		
		return retValue;
	}
	
}
