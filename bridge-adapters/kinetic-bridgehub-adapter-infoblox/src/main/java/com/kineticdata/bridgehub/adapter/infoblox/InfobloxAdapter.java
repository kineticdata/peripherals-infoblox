package com.kineticdata.bridgehub.adapter.infoblox;

import com.kineticdata.bridgehub.adapter.BridgeAdapter;
import com.kineticdata.bridgehub.adapter.BridgeError;
import com.kineticdata.bridgehub.adapter.BridgeRequest;
import com.kineticdata.bridgehub.adapter.BridgeUtils;
import com.kineticdata.bridgehub.adapter.Count;
import com.kineticdata.bridgehub.adapter.Record;
import com.kineticdata.bridgehub.adapter.RecordList;
import static com.kineticdata.bridgehub.adapter.infoblox.InfobloxAdapter.logger;
import com.kineticdata.commons.v1.config.ConfigurableProperty;
import com.kineticdata.commons.v1.config.ConfigurablePropertyMap;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.LoggerFactory;


public class InfobloxAdapter implements BridgeAdapter {
    /*----------------------------------------------------------------------------------------------
     * PROPERTIES
     *--------------------------------------------------------------------------------------------*/

    /** Defines the adapter display name */
    public static final String NAME = "Infoblox Bridge";

    /** Defines the logger */
    protected static final org.slf4j.Logger logger = LoggerFactory.getLogger(InfobloxAdapter.class);

    /** Adapter version constant. */
    public static String VERSION;
    /** Load the properties version from the version.properties file. */
    static {
        try {
            java.util.Properties properties = new java.util.Properties();
            properties.load(InfobloxAdapter.class.getResourceAsStream("/"+InfobloxAdapter.class.getName()+".version"));
            VERSION = properties.getProperty("version");
        } catch (IOException e) {
            logger.warn("Unable to load "+InfobloxAdapter.class.getName()+" version properties.", e);
            VERSION = "Unknown";
        }
    }

    /** Defines the collection of property names for the adapter */
    public static class Properties {
        public static final String PROPERTY_HOST = "Host";
        public static final String PROPERTY_USERNAME = "Username";
        public static final String PROPERTY_PASSWORD = "Password";
    }

    private final ConfigurablePropertyMap properties = new ConfigurablePropertyMap(
        new ConfigurableProperty(Properties.PROPERTY_USERNAME).setIsRequired(true),
        new ConfigurableProperty(Properties.PROPERTY_PASSWORD).setIsRequired(true).setIsSensitive(true),
        new ConfigurableProperty(Properties.PROPERTY_HOST).setIsRequired(true)
    );

    private String username;
    private String password;
    private String host;

    /*---------------------------------------------------------------------------------------------
     * SETUP METHODS
     *-------------------------------------------------------------------------------------------*/

    @Override
    public void initialize() throws BridgeError {
        this.username = properties.getValue(Properties.PROPERTY_USERNAME);
        this.password = properties.getValue(Properties.PROPERTY_PASSWORD);
        this.host = StringUtils.removeEnd(properties.getValue(Properties.PROPERTY_HOST), "/");

    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public void setProperties(Map<String,String> parameters) {
        properties.setValues(parameters);
    }

    @Override
    public ConfigurablePropertyMap getProperties() {
        return properties;
    }

    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    @Override
    public Count count(BridgeRequest request) throws BridgeError {
        // Build the URL
        StringBuilder buildUrl = new StringBuilder();
        buildUrl.append(this.host);
        buildUrl.append("/wapi/v1.0/");
        buildUrl.append(request.getStructure());
        
        // Parsing the query to include parameters if they have been used. 
        InfobloxQualificationParser parser = new InfobloxQualificationParser();
        String query = parser.parse(request.getQuery(),request.getParameters());
        
        if (!query.equals("") || !query.equals("network=*")) {
            buildUrl.append("?").append(encodeQuery(query));
        }
        
        String output = "";
        try (CloseableHttpClient httpclient = createAcceptSelfSignedCertificateClient()) {
            HttpGet get = new HttpGet(buildUrl.toString());
            
            // Setting up the basic authentication and appending it to the Http object
            logger.trace("Appending the authorization header to the post call");
            String creds = this.username + ":" + this.password;
            byte[] basicAuthBytes = Base64.encodeBase64(creds.getBytes());
            get.setHeader("Authorization", "Basic " + new String(basicAuthBytes));
            
            HttpResponse response = httpclient.execute(get);
            output = EntityUtils.toString(response.getEntity());
            logger.trace("Request response code: " + response.getStatusLine().getStatusCode());
            logger.trace("Request response: " + output);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | IOException e) {
            throw new BridgeError(e);
        }
        
        JSONArray jsonArray;
        try {
            jsonArray = (JSONArray)JSONValue.parse(output);
            if (jsonArray == null) throw new RuntimeException("The returned output is not valid JSON and therefore cannot be parsed. Turn on trace logging to see the full output the adapter is recieving.");
        } catch (Exception e) {
            throw new BridgeError("Error: Unable to parse JSON",e);
        }

        return new Count(jsonArray.size());
    }

    @Override
    public Record retrieve(BridgeRequest request) throws BridgeError {
        StringBuilder buildUrl = new StringBuilder();
        buildUrl.append(this.host);
        buildUrl.append("/wapi/v1.0/");
        buildUrl.append(request.getStructure());
        
        List<String> fields = request.getFields();
        List<String> returnableFields = new ArrayList<String>();
        for (String field : fields) {
            if (field.matches("extensible_attributes\\..*") && !returnableFields.contains("extensible_attributes")) {
                returnableFields.add("extensible_attributes");
            } else if (!field.equals("_ref") && !field.matches("extensible_attributes\\..*")) {
                returnableFields.add(fieldToFieldList(field).get(0));
            }
        }

        buildUrl.append("?_return_fields=");
        buildUrl.append(StringUtils.join(returnableFields,","));
        
        // Parsing the query to include parameters if they have been used. 
        InfobloxQualificationParser parser = new InfobloxQualificationParser();
        String query = parser.parse(request.getQuery(),request.getParameters());
        
        if (!query.equals("") || !query.equals("network=*")) {
            buildUrl.append("&").append(encodeQuery(query));
        }
        
        String output = "";
        try (CloseableHttpClient httpclient = createAcceptSelfSignedCertificateClient()) {
            HttpGet get = new HttpGet(buildUrl.toString());
            
            // Setting up the basic authentication and appending it to the Http object
            logger.trace("Appending the authorization header to the post call");
            String creds = this.username + ":" + this.password;
            byte[] basicAuthBytes = Base64.encodeBase64(creds.getBytes());
            get.setHeader("Authorization", "Basic " + new String(basicAuthBytes));
            
            HttpResponse response = httpclient.execute(get);
            output = EntityUtils.toString(response.getEntity());
            logger.trace("Request response code: " + response.getStatusLine().getStatusCode());
            logger.trace("Request response: " + output);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | IOException e) {
            throw new BridgeError(e);
        }
        
        JSONArray jsonArray;
        try {
            jsonArray = (JSONArray)JSONValue.parse(output);
            if (jsonArray == null) throw new RuntimeException("The returned output is not valid JSON and therefore cannot be parsed. Turn on trace logging to see the full output the adapter is recieving.");
        } catch (Exception e) {
            throw new BridgeError("Error: Unable to parse JSON",e);
        }
        
        Map<String,Object> record = null;
        if (jsonArray.size() > 1) {
            throw new BridgeError("Multiple results matched an expected single match query");
        } else if (!jsonArray.isEmpty()) {
            record = new LinkedHashMap<>();
            JSONObject json = (JSONObject)JSONValue.parse(jsonArray.get(0).toString());
            for (String field : request.getFields()) {
                if (field.matches("extensible_attributes\\..*")) {
                    String subField = field.substring("extensible_attributes.".length());
                    JSONObject extattr = (JSONObject)JSONValue.parse(json.get("extensible_attributes").toString());
                    record.put(field,extattr.get(subField));
                } else {
                    record.put(field,toString(getBridgeField(json,fieldToFieldList(field))));
                }
            }
        }
        
        // Returning the response
        return new Record(record);
    }

    @Override
    public RecordList search(BridgeRequest request) throws BridgeError {
        StringBuilder buildUrl = new StringBuilder();
        buildUrl.append(this.host);
        buildUrl.append("/wapi/v1.0/");
        buildUrl.append(request.getStructure());
        
        List<String> fields = request.getFields();
        List<String> returnableFields = new ArrayList<String>();
        for (String field : fields) {
            if (field.matches("extensible_attributes\\..*") && !returnableFields.contains("extensible_attributes")) {
                returnableFields.add("extensible_attributes");
            } else if (!field.equals("_ref") && !field.matches("extensible_attributes\\..*")) {
                returnableFields.add(fieldToFieldList(field).get(0));
            }
        }

        buildUrl.append("?_return_fields=");
        buildUrl.append(StringUtils.join(returnableFields,","));
        
        // Parsing the query to include parameters if they have been used. 
        InfobloxQualificationParser parser = new InfobloxQualificationParser();
        String query = parser.parse(request.getQuery(),request.getParameters());
        
        if (!query.equals("") && !query.equals("network=*")) {
            buildUrl.append("&").append(encodeQuery(query));
        }
        
        String output = "";
        try (CloseableHttpClient httpclient = createAcceptSelfSignedCertificateClient()) {
            HttpGet get = new HttpGet(buildUrl.toString());
            
            // Setting up the basic authentication and appending it to the Http object
            logger.trace("Appending the authorization header to the post call");
            String creds = this.username + ":" + this.password;
            byte[] basicAuthBytes = Base64.encodeBase64(creds.getBytes());
            get.setHeader("Authorization", "Basic " + new String(basicAuthBytes));
            
            HttpResponse response = httpclient.execute(get);
            output = EntityUtils.toString(response.getEntity());
            logger.trace("Request response code: " + response.getStatusLine().getStatusCode());
            logger.trace("Request response: " + output);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | IOException e) {
            throw new BridgeError(e);
        }
        
        JSONArray jsonArray;
        try {
            jsonArray = (JSONArray)JSONValue.parse(output);
            if (jsonArray == null) throw new RuntimeException("The returned output is not valid JSON and therefore cannot be parsed. Turn on trace logging to see the full output the adapter is recieving.");
        } catch (Exception e) {
            throw new BridgeError("Error: Unable to parse JSON",e);
        }
        
        List<Record> records = new ArrayList<>();
        for (int i = 0; i<jsonArray.size(); i++) {
            JSONObject record = (JSONObject)jsonArray.get(i);
            JSONObject extensibleAttributes = null;
            // Initialize Map for Record data
            Map<String,Object> data = new LinkedHashMap<>();
            for (String key : request.getFields()) {
                if (key.startsWith("extensible_attributes.")) {
                    String subKey = key.substring("extensible_attributes.".length());
                    if (extensibleAttributes == null) extensibleAttributes = (JSONObject)record.get("extensible_attributes");
                    data.put(key,extensibleAttributes.get(subKey));
                } else {
                    data.put(key,getBridgeField(record,fieldToFieldList(key)));
                }
            }
            records.add(new Record(data));
        }

        // Building the output metadata
        Map<String,String> metadata = BridgeUtils.normalizePaginationMetadata(request.getMetadata());
        metadata.put("pageSize", "0");
        metadata.put("pageNumber", "1");
        metadata.put("offset", "0");
        metadata.put("size", String.valueOf(records.size()));
        metadata.put("count", metadata.get("size"));

        // Returning the response
        return new RecordList(request.getFields(), records, metadata);
    }
    
    /*----------------------------------------------------------------------------------------------
    * PRIVATE HELPER METHODS
    *--------------------------------------------------------------------------------------------*/
    private static final Pattern isMultipleFields = Pattern.compile("\\w+(?:\\[\"\\w*?\"\\])*");
    private static final Pattern findChildFields = Pattern.compile("\\[\"(\\w*?)\"\\]");
    
    private List<String> fieldToFieldList(String field) {
        List<String> fieldList = new ArrayList<String>();
        if (isMultipleFields.matcher(field).matches()) {
            fieldList.add(field.split("\\[")[0]);
            Matcher m = findChildFields.matcher(field);
            while (m.find()) {
                fieldList.add(m.group(1));
            }
        } else {
            fieldList.add(field);
        }
        return fieldList;
    }
    
    private Object getBridgeField(Object parentObject, List<String> fieldList) {
        if (fieldList == null || fieldList.isEmpty()) {
            return parentObject;
        } else if (parentObject.getClass().equals(JSONObject.class)) {
            JSONObject json = (JSONObject)parentObject;
            String currentField = fieldList.get(0);
            return getBridgeField(json.get(currentField),fieldList.subList(1,fieldList.size()));
        } else if (parentObject.getClass().equals(JSONArray.class)) {
            JSONArray array = (JSONArray)parentObject;
            List returnList = new ArrayList();
            for (Object o : array) {
                returnList.add(getBridgeField(o,fieldList));
            }
            return returnList;
        } else {
            return null;
        }
    }
    
    /**
    * Returns the string value of the object.
    * <p>
    * If the value is not a String, a JSON representation of the object will be returned.
    * 
    * @param value
    * @return 
    */
    private String toString(Object value) {
        String result = null;
        if (value != null) {
            if (String.class.isInstance(value)) {
                result = (String)value;
            } else {
                result = JSONValue.toJSONString(value);
            }
        }
        return result;
     }

    private String encodeQuery(String query) {
        String encodedQuery = URLEncoder.encode(query);
        return encodedQuery.replaceAll("%3D", "=").replaceAll("%26", "&");
    }

    private static CloseableHttpClient createAcceptSelfSignedCertificateClient()
            throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

        // use the TrustSelfSignedStrategy to allow Self Signed Certificates
        SSLContext sslContext = SSLContextBuilder
                .create()
                .loadTrustMaterial(new TrustSelfSignedStrategy())
                .build();

        // we can optionally disable hostname verification. 
        // if you don't want to further weaken the security, you don't have to include this.
        HostnameVerifier allowAllHosts = new NoopHostnameVerifier();
        
        // create an SSL Socket Factory to use the SSLContext with the trust self signed certificate strategy
        // and allow all hosts verifier.
        SSLConnectionSocketFactory connectionFactory = new SSLConnectionSocketFactory(sslContext, allowAllHosts);
        
        // finally create the HttpClient using HttpClient factory methods and assign the ssl socket factory
        return HttpClients
                .custom()
                .setSSLSocketFactory(connectionFactory)
                .build();
    }
}