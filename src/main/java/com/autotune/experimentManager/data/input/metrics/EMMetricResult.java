package com.autotune.experimentManager.data.input.metrics;

import com.autotune.experimentManager.data.input.interfaces.ConvertToJSON;
import com.autotune.experimentManager.exceptions.IncompatibleInputJSONException;
import com.autotune.experimentManager.utils.EMConstants;
import org.json.JSONObject;

public class EMMetricResult implements ConvertToJSON {
    private EMMetricGenericResults emMetricGenericResults;
    private EMMetricPercentileResults emMetricPercentileResults;
    private EMMetricResultSummary emMetricResultSummary;
    private boolean isPercentileResultsAvailable;

    public EMMetricResultSummary getEmMetricResultSummary() {
        return emMetricResultSummary;
    }

    public void setEmMetricResultSummary(EMMetricResultSummary emMetricResultSummary) {
        this.emMetricResultSummary = emMetricResultSummary;
    }

    public EMMetricGenericResults getEmMetricGenericResults() {
        return emMetricGenericResults;
    }

    public void setEmMetricGenericResults(EMMetricGenericResults emMetricGenericResults) {
        this.emMetricGenericResults = emMetricGenericResults;
    }

    public EMMetricPercentileResults getEmMetricPercentileResults() {
        return emMetricPercentileResults;
    }

    public void setEmMetricPercentileResults(EMMetricPercentileResults emMetricPercentileResults) {
        this.emMetricPercentileResults = emMetricPercentileResults;
    }

    public boolean isPercentileResultsAvailable() {
        return isPercentileResultsAvailable;
    }

    public void setPercentileResultsAvailable(boolean percentileResultsAvailable) {
        isPercentileResultsAvailable = percentileResultsAvailable;
    }

    public EMMetricResult (boolean needsPercentileInfo) {
        emMetricGenericResults = new EMMetricGenericResults();
        emMetricResultSummary = new EMMetricResultSummary();
        emMetricPercentileResults = null;
        if (needsPercentileInfo == true) {
            emMetricPercentileResults = new EMMetricPercentileResults();
            isPercentileResultsAvailable = true;
        }

    }

    public EMMetricResult () {
        emMetricGenericResults = new EMMetricGenericResults();
        emMetricPercentileResults = new EMMetricPercentileResults();
    }

    public EMMetricResult(JSONObject jsonObject) throws IncompatibleInputJSONException {
        if (!(jsonObject.has(EMConstants.EMJSONKeys.GENERAL_INFO) ||
            jsonObject.has(EMConstants.EMJSONKeys.PERCENTILE_INFO))) {
            throw new IncompatibleInputJSONException();
        }
        if (jsonObject.has(EMConstants.EMJSONKeys.PERCENTILE_INFO)) {
            isPercentileResultsAvailable = true;
        }
        emMetricGenericResults = new EMMetricGenericResults(jsonObject.getJSONObject(EMConstants.EMJSONKeys.GENERAL_INFO));
        if (isPercentileResultsAvailable){
            emMetricPercentileResults = new EMMetricPercentileResults(jsonObject.getJSONObject(EMConstants.EMJSONKeys.PERCENTILE_INFO));
        }
    }

    @Override
    public JSONObject toJSON() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(EMConstants.EMJSONKeys.GENERAL_INFO, emMetricGenericResults.toJSON());
        jsonObject.put(EMConstants.EMJSONKeys.SUMMARY, emMetricResultSummary.toJSON());
        if (isPercentileResultsAvailable) {
            jsonObject.put(EMConstants.EMJSONKeys.PERCENTILE_INFO, emMetricPercentileResults.toJSON());
        }
        return jsonObject;
    }
}
