import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.text.DateFormat;
import java.time.format.DateTimeFormatter;
import java.text.SimpleDateFormat;
import java.text.ParseException;

import org.apache.commons.io.FilenameUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import com.ephesoft.dcma.script.IJDomScript;

import static java.nio.file.StandardCopyOption.*;

/*
YYYY = 4-digit year
DDD = 3-digit day of year (sometimes called Julian day)
X = 1-character Media Code (identifies the source of the content)
BBB = 3-digit Batch Number (sequential number)
SSS = 3-digit Sequence Number (sequential number assigned to each document within a batch)
 

Sample: 2020020W952521
*/

public class ScriptExport implements IJDomScript {

	private static final String BATCH_LOCAL_PATH = "BatchLocalPath";
	private static final String BATCH_INSTANCE_ID = "BatchInstanceIdentifier";
	private static final String EXT_BATCH_XML_FILE = "_batch.xml";
	private static String ZIP_FILE_EXT = ".zip";
	
	private static String BATCH_XML_EXPORT_FOLDER = "";
	private static String BATCH_EXPORT_FOLDER = "";
	private static String EXPORT_DELIMITER = ",";
	private static String EXPORT_COLUMN_HEADER = "";
	
	public Object execute(Document document, String methodName, String docIdentifier) {
		Exception exception = null;
		
		try {
			System.out.println("*************  Inside ExportScript scripts.");

			System.out.println("*************  Start execution of the ExportScript scripts.");

			if (null == document) {
				System.out.println("Input document is null.");
				return null;
			} else {
				boolean bWrite = false;
				
				init(document);
				
				System.out.println(BATCH_XML_EXPORT_FOLDER);
				
				String BatchValidatedBy = document.getRootElement().getChildText("BatchValidatedBy");
				String BatchCreationDate = document.getRootElement().getChildText("BatchCreationDate");
				
				//				
				String batchLocalPath = document.getRootElement().getChildText(BATCH_LOCAL_PATH);
				String batchInstanceId = document.getRootElement().getChildText(BATCH_INSTANCE_ID);
				String batchClassIdentifier = document.getRootElement().getChildText("BatchClassIdentifier");

				int batchNumber = getBatchNumber(batchLocalPath, batchClassIdentifier);
				
				String zeroFillLeftBatchNumber = String.format("%03d", batchNumber);
								
				//	
				Date todayDate = new Date();
				
				String currentYear = getCurrentYear(todayDate);
				
				int ordinalNumber = getOrdinalNumber(todayDate);
				
				String zeroFillLeftOrdinalNumber = String.format("%03d", ordinalNumber);
				
				List<Element> docList = document.getRootElement().getChild("Documents").getChildren("Document");
				
				List<Map<String, String>> valueMapList = new ArrayList<Map<String, String>>();
			 
		        for (Element doc : docList) {
					Map<String, String> indexMap = new HashMap<String, String>();
					
		        	String docIndentifier = doc.getChildText("Identifier");
		        	String docNumberText = docIndentifier.substring(3);
		        	int docNumber = Integer.parseInt(docNumberText);
		        	String zeroFilledLeftDocNumber = String.format("%03d", docNumber);
		        	
		        	String dcnNumber = String.format("%s%sG%s%s", 
		        			currentYear, zeroFillLeftOrdinalNumber, zeroFillLeftBatchNumber, zeroFilledLeftDocNumber);
		        	
		        	doc.addContent(new Element("DCNNumber").setText(dcnNumber)); 
		        	
		        	String dcnNumberFileName = String.format("%s.pdf", dcnNumber);
		        	
		        	System.out.println(dcnNumberFileName);
		        	
		        	String fileLocation = doc.getChildText("FinalMultiPagePdfFilePath");
		        	
		        	//fileLocation = fileLocation.replace("\\", "/").substring(2);
		        	fileLocation = fileLocation.replace("\\", "\\\\");
		        	
		        	System.out.println(fileLocation);
		        	
		        	Path sourceFileLocation = Paths.get(fileLocation);
		        	
		        	Path destinationFileLocation = Paths.get(BATCH_EXPORT_FOLDER, dcnNumberFileName);
		        		        	
					System.out.println(sourceFileLocation);
		        	System.out.println(destinationFileLocation);
		        	
		        	Files.move(sourceFileLocation, destinationFileLocation, REPLACE_EXISTING);
					
					indexMap.put("CREATEDDATE", getISODateFormat(BatchCreationDate));
		        	indexMap.put("CREATEDUSERNAME", doc.getChildText("ValidatedBy"));
		        	indexMap.put("DATERECEIVED", getISODateFormat(BatchCreationDate));
		        	indexMap.put("SCANDATE", getISODateFormat(BatchCreationDate));
		        	
		        	indexMap.put("DOCTYPE", doc.getChildText("Type"));
		        	indexMap.put("DOCUMENTTITLE", dcnNumber + ".pdf");
		        	indexMap.put("DCN", dcnNumber);
		        	
					List<Element> indexList = doc.getChild("DocumentLevelFields").getChildren("DocumentLevelField");
					for (Element index : indexList) {
						String indexValue = index.getChildText("Value");
						String indexName = index.getChildText("Name");
						indexMap.put(indexName, indexValue);
						
						System.out.println(indexName + " - " + indexValue);
					}
					
					String docType = doc.getChildText("Type");
					
		        	if (docType.contains("CMS1500")) {
			        	indexMap.put("MEMBERID", indexMap.get("HCFA01A"));
			        	indexMap.put("NPI", indexMap.get("HCFA24JNPI1"));
			        	indexMap.put("PROVIDERNAME", indexMap.get("HCFA33_Line1"));
		        	} else if (docType.contains("UB04")) {
			        	indexMap.put("MEMBERID", indexMap.get("UB3apatcntl"));
			        	indexMap.put("NPI", indexMap.get("UB56npi"));
			        	indexMap.put("PROVIDERNAME", indexMap.get("UB1providerName"));	
		        	}
		        	
					valueMapList.add(indexMap);
		        }
		        
				/*				
		        for (Element doc : docList) {
		        	String DCNNumber = doc.getChildText("DCNNumber");
		        	System.out.println(DCNNumber);
		        }
		        */
		        
				// Write the document object to the xml file. Currently following IF block is commented for performance improvement.
				if (bWrite) {					
					writeToXML(document);
					System.out.println("*************  Successfully write the xml file for the ExportScript scripts.");
				}
				
		        String batchName = String.format("%s%sG%s%s", 
	        			currentYear, zeroFillLeftOrdinalNumber, zeroFillLeftBatchNumber, ".xml");
		        
				copyBatch(batchInstanceId, batchName);
				
				updateBatchXML(batchInstanceId, batchName, document);
				
				String exportName = String.format("%s%sG%s%s", 
	        			currentYear, zeroFillLeftOrdinalNumber, zeroFillLeftBatchNumber, ".txt");
						
				writeMetadataFile(valueMapList);
			}
			
			System.out.println("*************  End execution of the ScriptExport scripts.");
		} catch (Exception e) {
			System.out.println("*************  Error occurred in scripts." + e.getMessage());
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
			System.err.println("Unable to find the local folder path in batch xml file.");
			return;
		}

		String batchInstanceID = null;
		List<?> batchInstanceIDList = document.getRootElement().getChildren(BATCH_INSTANCE_ID);
		if (null != batchInstanceIDList) {
			batchInstanceID = ((Element) batchInstanceIDList.get(0)).getText();

		}

		if (null == batchInstanceID) {
			System.err.println("Unable to find the batch instance ID in batch xml file.");
			return;
		}

		String batchXMLPath = batchLocalPath.trim() + File.separator + batchInstanceID + File.separator + batchInstanceID
				+ EXT_BATCH_XML_FILE;

		String batchXMLZipPath = batchXMLPath + ZIP_FILE_EXT;

		System.out.println("batchXMLZipPath************" + batchXMLZipPath);

		OutputStream outputStream = null;
		File zipFile = new File(batchXMLZipPath);
		FileWriter writer = null;
		XMLOutputter out = new com.ephesoft.dcma.batch.encryption.util.BatchInstanceXmlOutputter(batchInstanceID);
		try {
			if (zipFile.exists()) {
				System.out.println("Found the batch xml zip file.");
				outputStream = getOutputStreamFromZip(batchXMLPath, batchInstanceID + EXT_BATCH_XML_FILE);
				out.output(document, outputStream);
			} else {
				writer = new java.io.FileWriter(batchXMLPath);
				out.output(document, writer);
				writer.flush();
				writer.close();
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
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
	
	private int getBatchNumber(String batchLocalPath, String batchClassId) throws Exception {
		String pathToBatchNumber = batchLocalPath.replaceFirst("ephesoft-system-folder", "") + batchClassId
				+ File.separator + "script-config" + File.separator + "batch.dat";
		
		//String dir = System.getProperty("user.dir");
		//Path pathToBatchNumber = Paths.get(dir, "scripts", "OCRSolutions-Accenture", "batch.dat");
		
		String content = new Scanner(new File(pathToBatchNumber.toString())).useDelimiter("\\Z").next();
		
		int batchNumber = Integer.parseInt(content);
		
		FileWriter writer = new FileWriter(pathToBatchNumber.toString());

		writer.write(String.valueOf(++batchNumber)); 
		writer.flush();
		writer.close();
		
		return batchNumber;
	}
	
	private static String getCurrentYear(Date todayDate) {
		
		String currentYear = new SimpleDateFormat("YYYY").format(todayDate);
				
		return currentYear;
	}
	
	private static int getOrdinalNumber(Date todayDate) {
				
		String ordinalNumber = new SimpleDateFormat("D").format(todayDate);
				
		return Integer.parseInt(ordinalNumber);
	}
	
	private void copyBatch(String batchInstanceId, String batchName) throws Exception {
		String sourceFile = BATCH_XML_EXPORT_FOLDER + File.separator + batchInstanceId + EXT_BATCH_XML_FILE;
		
		String destinationFile = BATCH_XML_EXPORT_FOLDER + File.separator + batchName;
		
		System.out.println(sourceFile + " " + destinationFile);
		
		Path sourceFileLocation = Paths.get(sourceFile);
		        	
		Path destinationFileLocation = Paths.get(destinationFile);
		
		Files.move(sourceFileLocation, destinationFileLocation, REPLACE_EXISTING);
		
	}

	private void init(Document document) throws Exception {
		
		Properties properties = getProperties(document, "ExportConfig.properties");
		
		BATCH_XML_EXPORT_FOLDER = properties.getProperty("BATCH_XML_EXPORT_FOLDER");
		BATCH_EXPORT_FOLDER = properties.getProperty("BATCH_EXPORT_FOLDER");
		EXPORT_DELIMITER = properties.getProperty("EXPORT_DELIMITER");
		EXPORT_COLUMN_HEADER = properties.getProperty("EXPORT_COLUMN_HEADER").replaceAll(",", EXPORT_DELIMITER);
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
	
	private void updateBatchXML(String batchInstanceID, String batchName, Document document) {

		OutputStream outputStream = null;
		FileWriter writer = null;
		
		String destinationFile = BATCH_XML_EXPORT_FOLDER + File.separator + batchName;
		
		XMLOutputter out = new XMLOutputter();
		
		try {
			writer = new java.io.FileWriter(destinationFile);
			out.setFormat(Format.getPrettyFormat());
			out.output(document, writer);
			writer.flush();
			writer.close();
		} catch (Exception e) {
			System.err.println(e.getMessage());
		} finally {
			if (outputStream != null) {
				try {
					outputStream.close();
				} catch (IOException e) {
				}
			}
		}
	}
	
	private void writeMetadataFile(List<Map<String, String>> valueMapList) throws IOException {

	
		for (int i = 0; i < valueMapList.size(); i++) {
			
			Map<String, String> mapValue = valueMapList.get(i);
			
			String DCN = mapValue.get("DCN");
			
			String filePath = BATCH_EXPORT_FOLDER + File.separator + DCN + ".txt";
			
			FileWriter writer = new FileWriter(filePath);
		
			System.out.println(EXPORT_COLUMN_HEADER);
		
			writer.append(EXPORT_COLUMN_HEADER);
			writer.append(System.getProperty("line.separator"));
			
			String DOCCLASS = mapValue.get("DOCCLASS");
			if (DOCCLASS != null) {
				System.out.print(DOCCLASS + EXPORT_DELIMITER);
				writer.append(DOCCLASS + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String SUBCLASS = mapValue.get("SUBCLASS");
			if (SUBCLASS != null) {
				System.out.print(SUBCLASS + EXPORT_DELIMITER);
				writer.append(SUBCLASS + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String API = mapValue.get("API");
			if (API != null) {
				System.out.print(API + EXPORT_DELIMITER);
				writer.append(API + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
						
			String CREATEDDATE = mapValue.get("CREATEDDATE");
			if (CREATEDDATE != null) {
				System.out.print(CREATEDDATE + EXPORT_DELIMITER);
				writer.append(CREATEDDATE + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String CREATEDUSERNAME = mapValue.get("CREATEDUSERNAME");
			if (CREATEDUSERNAME != null) {
				System.out.print(CREATEDUSERNAME + EXPORT_DELIMITER);
				writer.append(CREATEDUSERNAME + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String DATERECEIVED = mapValue.get("DATERECEIVED");
			if (DATERECEIVED != null) {
				System.out.print(DATERECEIVED + EXPORT_DELIMITER);
				writer.append(DATERECEIVED + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			//String DCN = mapValue.get("DCN");
			if (DCN != null) {
				System.out.print(DCN + EXPORT_DELIMITER);
				writer.append(DCN + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String DOCTYPE = mapValue.get("DOCTYPE");
			if (DOCTYPE != null) {
				System.out.print(DOCTYPE + EXPORT_DELIMITER);
				writer.append(DOCTYPE + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String DOCUMENTTITLE = mapValue.get("DOCUMENTTITLE");
			if (DOCUMENTTITLE != null) {
				System.out.print(DOCUMENTTITLE + EXPORT_DELIMITER);
				writer.append(DOCUMENTTITLE + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String FACILITYNAME = mapValue.get("FACILITYNAME");
			if (FACILITYNAME != null) {
				System.out.print(FACILITYNAME + EXPORT_DELIMITER);
				writer.append(FACILITYNAME + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String ICN = mapValue.get("ICN");
			if (ICN != null) {
				System.out.print(ICN + EXPORT_DELIMITER);
				writer.append(ICN + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String LETTERID = mapValue.get("LETTERID");
			if (LETTERID != null) {
				System.out.print(LETTERID + EXPORT_DELIMITER);
				writer.append(LETTERID + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String LETTERNAME = mapValue.get("LETTERNAME");
			if (LETTERNAME != null) {
				System.out.print(LETTERNAME + EXPORT_DELIMITER);
				writer.append(LETTERNAME + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String LETTERTYPE = mapValue.get("LETTERTYPE");
			if (LETTERTYPE != null) {
				System.out.print(LETTERTYPE + EXPORT_DELIMITER);
				writer.append(LETTERTYPE + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
	
			String MEMBERID = mapValue.get("MEMBERID");
			if (MEMBERID != null) {
				System.out.print(MEMBERID + EXPORT_DELIMITER);
				writer.append(MEMBERID + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String MODIFIEDDATE = mapValue.get("MODIFIEDDATE");
			if (MODIFIEDDATE != null) {
				System.out.print(MODIFIEDDATE + EXPORT_DELIMITER);
				writer.append(MODIFIEDDATE + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String MODIFIEDUSERNAME = mapValue.get("MODIFIEDUSERNAME");
			if (MODIFIEDUSERNAME != null) {
				System.out.print(MODIFIEDUSERNAME + EXPORT_DELIMITER);
				writer.append(MODIFIEDUSERNAME + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String NPI = mapValue.get("NPI");
			if (NPI != null) {
				System.out.print(NPI + EXPORT_DELIMITER);
				writer.append(NPI + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String PROVIDERNAME = mapValue.get("PROVIDERNAME");
			if (PROVIDERNAME != null) {
				System.out.print(PROVIDERNAME + EXPORT_DELIMITER);
				writer.append(PROVIDERNAME + EXPORT_DELIMITER);
			} else {
				System.out.print("" + EXPORT_DELIMITER);
				writer.append("" + EXPORT_DELIMITER);
			}
			
			String SCANDATE = mapValue.get("SCANDATE");
			if (SCANDATE != null) {
				System.out.print(SCANDATE);
				writer.append(SCANDATE);
			} else {
				System.out.print("");
				writer.append("");
			}
			
			System.out.println("");		
			writer.append(System.getProperty("line.separator"));
			
			writer.flush();
			writer.close();
		}
		
	}
	
	private String getISODateFormat(String dateString) throws Exception {
		
		SimpleDateFormat informat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Date date = informat.parse(dateString);
		
		SimpleDateFormat outDateformat = new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat outTimeformat = new SimpleDateFormat("HH:mm:ss");
		return outDateformat.format(date) + "T" + outTimeformat.format(date);
	}
}
