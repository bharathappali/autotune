package com.autotune.experimentManager.data.input.info;

import com.autotune.experimentManager.data.input.interfaces.ConvertToJSON;
import com.autotune.experimentManager.data.input.interfaces.ConvertToJSONArray;
import com.autotune.experimentManager.exceptions.IncompatibleInputJSONException;
import com.autotune.experimentManager.utils.EMConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class EMDataSourceInfo implements ConvertToJSONArray {
    private static final Logger LOGGER = LoggerFactory.getLogger(EMDataSourceInfo.class);
    private ArrayList<EMDataSourceDefinition> datasources;

    public EMDataSourceInfo(JSONObject jsonObject) throws IncompatibleInputJSONException {
        LOGGER.info("Creating EMDataSourceInfo");
        if (!jsonObject.has(EMConstants.EMJSONKeys.DATASOURCE_INFO)) {
            throw  new IncompatibleInputJSONException();
        }
        JSONArray subObj = jsonObject.getJSONArray(EMConstants.EMJSONKeys.DATASOURCE_INFO);
        if (null != subObj) {
            datasources = new ArrayList<EMDataSourceDefinition>();
            for (Object obj : subObj) {
                JSONObject datasourceDefJson = (JSONObject) obj;
                EMDataSourceDefinition dataSourceDefinition = new EMDataSourceDefinition(datasourceDefJson);
                datasources.add(dataSourceDefinition);
            }
        } else {
            throw new IncompatibleInputJSONException();
        }
    }

    public ArrayList<EMDataSourceDefinition> getDatasources() {
        return datasources;
    }

    @Override
    public JSONArray toJSON() {
        JSONArray datasourceJsonArray = new JSONArray();
        for (EMDataSourceDefinition emDataSourceDefinition : datasources) {
            datasourceJsonArray.put(emDataSourceDefinition.toJSON());
        }
        return datasourceJsonArray;
    }
}
