package com.kineticdata.bridgehub.adapter.infoblox;

import com.kineticdata.bridgehub.adapter.QualificationParser;

public class InfobloxQualificationParser extends QualificationParser {
    public String encodeParameter(String name, String value) {
        return value;
    }
}
