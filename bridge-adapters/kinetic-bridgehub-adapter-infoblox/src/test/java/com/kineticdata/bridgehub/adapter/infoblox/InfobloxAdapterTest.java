package com.kineticdata.bridgehub.adapter.infoblox;

import com.kineticdata.bridgehub.adapter.BridgeAdapterTestBase;
import com.kineticdata.bridgehub.adapter.BridgeError;
import com.kineticdata.bridgehub.adapter.BridgeRequest;
import com.kineticdata.bridgehub.adapter.Record;
import com.kineticdata.bridgehub.adapter.RecordList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfobloxAdapterTest extends BridgeAdapterTestBase  {
    private static final Logger LOGGER = LoggerFactory
        .getLogger(InfobloxAdapterTest.class);
    static String userRecordsMockData = null;

    @Override
    public String getConfigFilePath() {
        return "src/test/resources/bridge-config.yml";
    }
    
    @Override
    public Class getAdapterClass() {
        return InfobloxAdapter.class;
    }
    
    @Test
    public void test_search_network_VLAN() throws Exception {
        BridgeRequest request = new BridgeRequest();
        BridgeError error = null;
        
        request.setStructure("network");
        request.setQuery("*VLAN~=^<%=parameter[\"VLAN\"]%>");
        
        Map parameters = new HashMap();
        parameters.put("VLAN", "300");
        request.setParameters(parameters);
        
        List<String> fields = 
            Arrays.asList("network","comment","_ref","extensible_attributes.VLAN");
        request.setFields(fields);
        
            RecordList records = null;
        try {
            records = getAdapter().search(request);
        } catch (BridgeError e) {
            error = e;
        }
        
        // Check that new bridge error was thrown
        assertNull(error);        
        assertTrue(records.getRecords().size() > 0);
    }
 
    @Test
    public void test_search_network() throws Exception {
        BridgeRequest request = new BridgeRequest();
        BridgeError error = null;
        
        request.setStructure("network");
        request.setQuery("network~=<%=parameter[\"Network\"]%>");
        
        Map parameters = new HashMap();
        parameters.put("Network", "10.203.78.128/25");
        request.setParameters(parameters);
        
        List<String> fields = 
            Arrays.asList("network","comment","_ref","extensible_attributes.VLAN");
        request.setFields(fields);
        
        RecordList records = null;
        try {
            records = getAdapter().search(request);
        } catch (BridgeError e) {
            error = e;
        }
        
        // Check that new bridge error was thrown
        assertNull(error);
        assertTrue(records.getRecords().size() > 0);
    }
    
    @Test
    public void test_retrieve_network() throws Exception {
        BridgeRequest request = new BridgeRequest();
        BridgeError error = null;
                
        request.setStructure("network");
        request.setQuery("contains_address=<%=parameter[\"IP\"]%>");
        
        Map parameters = new HashMap();
        parameters.put("IP", "10.203.78.143");
        request.setParameters(parameters);
        
        List<String> fields = 
            Arrays.asList("network","comment","_ref","extensible_attributes.VLAN");
        request.setFields(fields);
        
        Record record = new Record();
        try {
            // Simulate bridge call
            record = getAdapter().retrieve(request);
        } catch (BridgeError e) {
            error = e;
        }

        // Check that new bridge error was thrown
        assertNull(error);
        // If a matching car is not found the record will be null.
        assertNotNull(record.getRecord());
    }

    @Test
    public void test_search_record_host() throws Exception {
        BridgeRequest request = new BridgeRequest();
        BridgeError error = null;
        
        request.setStructure("record:host");
        request.setQuery("name~=<%=parameter[\"Name\"]%>");
        
        Map parameters = new HashMap();
        parameters.put("Name", "oirerms9994");
        request.setParameters(parameters);
        
        List<String> fields = 
            Arrays.asList("ipv4addrs","name","_ref","view");
        request.setFields(fields);
        
        RecordList records = null;
        try {
            records = getAdapter().search(request);
        } catch (BridgeError e) {
            error = e;
        }
        
        // Check that new bridge error was thrown
        assertNull(error);
        assertTrue(records.getRecords().size() > 0);
    }    
    
    @Test
    public void test_search_ipvaddr() throws Exception {
        BridgeRequest request = new BridgeRequest();
        BridgeError error = null;
        
        request.setStructure("ipv4address");
        request.setQuery("ip_address=<%=parameter[\"IP Address\"]%>");
        
        Map parameters = new HashMap();
        parameters.put("IP Address", "10.203.78.143");
        request.setParameters(parameters);
        
        List<String> fields = 
            Arrays.asList("ip_address","names","network");
        request.setFields(fields);
        
        RecordList records = null;
        try {
            records = getAdapter().search(request);
        } catch (BridgeError e) {
            error = e;
        }
        
        // Check that new bridge error was thrown
        assertNull(error);
        assertTrue(records.getRecords().size() > 0);
    } 
    
    @Test
    public void test_search_zone_auth() throws Exception {
        BridgeRequest request = new BridgeRequest();
        BridgeError error = null;
        
        request.setStructure("zone_auth");
        request.setQuery("fqdn~=<%=parameter[\"FQDN\"]%>");
        
        Map parameters = new HashMap();
        parameters.put("FQDN", "nitckc");
        request.setParameters(parameters);
        
        List<String> fields = 
            Arrays.asList("fqdn");
        request.setFields(fields);
        
        RecordList records = null;
        try {
            records = getAdapter().search(request);
        } catch (BridgeError e) {
            error = e;
        }
        
        // Check that new bridge error was thrown
        assertNull(error);
        assertTrue(records.getRecords().size() > 0);
    } 
}
