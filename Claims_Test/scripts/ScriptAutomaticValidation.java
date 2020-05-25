import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.ephesoft.dcma.util.XMLUtil;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

import com.ephesoft.dcma.script.IJDomScript;
import com.ephesoft.dcma.util.logger.EphesoftLogger;
import com.ephesoft.dcma.util.logger.ScriptLoggerFactory;
import com.ephesoft.dcma.core.component.ICommonConstants;
import com.ephesoft.dcma.util.ApplicationConfigProperties;

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


/**
 * The <code>ScriptAutomaticValidation</code> class represents the script execute structure. Writer of scripts plug-in should implement
 * this IScript interface to execute it from the scripting plug-in. Via implementing this interface writer can change its java file at
 * run time. Before the actual call of the java Scripting plug-in will compile the java and run the new class file.
 * 
 * @author Ephesoft
 * @version 1.0
 */
public class ScriptAutomaticValidation implements IJDomScript {

	private static EphesoftLogger LOGGER = ScriptLoggerFactory.getLogger(ScriptAutomaticValidation.class);
	
	private static String DOCUMENT = "Document";
	private static String DOCUMENTS = "Documents";
	private static String DOCUMENT_LEVEL_FIELDS = "DocumentLevelFields";
	private static String TRUE = "true";
	private static String FALSE = "false";
	private static String TYPE = "Type";
	private static String VALUE = "Value";
	private static String BATCH_LOCAL_PATH = "BatchLocalPath";
	private static String BATCH_INSTANCE_ID = "BatchInstanceIdentifier";
	private static String EXT_BATCH_XML_FILE = "_batch.xml";
	private static String VALID = "Valid";
	private static String PATTERN = "dd/MM/yyyy";
	private static String DATE = "DATE";
	private static String LONG = "LONG";
	private static String DOUBLE = "DOUBLE";
	private static String STRING = "STRING";
	private static String ZIP_FILE_EXT = ".zip";

	private static String VALIDATE_PROVIDER_HOST = "";
	private static String VALIDATE_MEMBER_HOST = "";

	
	/**
	 * The <code>execute</code> method will execute the script written by the writer at run time with new compilation of java file. It
	 * will execute the java file dynamically after new compilation.
	 * 
	 * @param document {@link Document}
	 */
	public Object execute(Document document, String methodName, String documentIdentifier) {
		Exception exception = null;
		
		try {
			LOGGER.info("*************  Inside ScriptAutomaticValidation scripts.");

			LOGGER.info("*************  Start execution of ScriptAutomaticValidation scripts.");

			if (document == null) {
				throw new Exception("Input document is null.");
			} else {
				
				boolean isWrite = true;
				
				init(document);

				Element root = document.getRootElement();
				
				String batchClassIdentifier = root.getChildText("BatchClassIdentifier");
				
				List<Element> docList = root.getChild("Documents").getChildren("Document");
								
				for (Element doc : docList) {
					
					String docType = doc.getChildText("Type");
					
					List<Element> docLevelField = doc.getChild("DocumentLevelFields").getChildren("DocumentLevelField");
					
					for (int idx = 0; idx < docLevelField.size(); idx++) {
						Element field = docLevelField.get(idx);
						
						if (field != null) {

							String name = field.getChildText("Name");
							String value = field.getChildText("Value");
							
							if (value != null && value.trim().length() > 0) {
								String confidenceValue = "";
								
								if (isNPIField(name)) {
									System.out.println(name + " = " + value);
									LOGGER.error("*********************** " + name + " = " + value);
									confidenceValue = isValidNPI(value) ? "100" : "0";
									
									if (confidenceValue == "0") {
										field.getChild("Value").setText("Invalid NPI");
									}	
								} 
								
								/*
								else if (isMemberField(name)) {
									System.out.println(name + " = " + value);
									LOGGER.error("*********************** " + name + " = " + value);
									confidenceValue = isValidMember(value) ? "100" : "0";
									doc.getChild("Confidence").setText(confidenceValue);
								}
								*/
							}
						}	
					}	
					
					if (docType.contains("UB04") && false) {
						HashMap<String, String> memberIDFields = new HashMap<String, String>();
						
						
						memberIDFields.put("60InsID", "Table58-62");
						
						for (String field : memberIDFields.keySet()) {
							String table = memberIDFields.get(field);
							
							String search = String.format(".//DataTable[Name='%s']//Rows/Row/Columns/Column[Name='%s']", table, field);
							List<Element> columns = XPath.newInstance(search).selectNodes(doc);
							
							for (Element column : columns) {
								String name = column.getChildText("Name");
								String value = column.getChildText("Value");
								
								if (value != null && value.trim().length() > 0) {
									System.out.println(name + " = " + value);
									LOGGER.info("*********************** " + name + " = " + value);
								    
									String confidenceValue = isValidMember(value) ? "100" : "0";
									doc.getChild("Confidence").setText(confidenceValue);
								}
							}							
						}	
					}
				}	
								
				if (isWrite) {					
					writeToXML(document);
					LOGGER.info("*************  Successfully write the xml file for the ScriptAutomaticValidation scripts.");
				}

			}
			
			LOGGER.info("*************  End execution of the ScriptAutomaticValidation scripts.");
		} catch (Exception e) {
			LOGGER.error("*************  Error occurred in scripts." + e.getMessage());
			e.printStackTrace();
			exception = e;
		}
		
		return exception;
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
			
			//Path propFileLocation = Paths.get(".","scripts", "OCRSolutions-Accenture", propertyFileName);
			
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
			case "HCFA33aproviderNPI":
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
				retValue = true;
				break;
		}
		
		return retValue;
	}
	

}
