package com.autotune.experimentManager.data.input.info;

import com.autotune.experimentManager.data.input.abscls.DataEditor;
import com.autotune.experimentManager.data.input.interfaces.ConvertToJSON;
import com.autotune.experimentManager.exceptions.EMDataObjectIsInEditingException;
import com.autotune.experimentManager.exceptions.EMDataObjectIsNotFilledException;
import com.autotune.experimentManager.exceptions.IncompatibleInputJSONException;
import com.autotune.experimentManager.utils.EMConstants;
import org.json.JSONObject;

public class EMConfigInfo extends DataEditor<EMConfigInfo> implements ConvertToJSON {

    private String trialId;
    private int trialNum;
    private String trialResultUrl;

    public String getTrialResultUrl() {
        return trialResultUrl;
    }

    public void setTrialResultUrl(String trialResultUrl) {
        this.trialResultUrl = trialResultUrl;
    }

    public String getTrialId() {
        return trialId;
    }

    public void setTrialId(String trialId) {
        this.trialId = trialId;
    }

    public int getTrialNum() {
        return trialNum;
    }

    public void setTrialNum(int trialNum) {
        this.trialNum = trialNum;
    }

    public EMConfigInfo(JSONObject jsonObject) throws IncompatibleInputJSONException {
        if (!jsonObject.has(EMConstants.EMJSONKeys.INFO)) {
            throw  new IncompatibleInputJSONException();
        }
        JSONObject subObj = jsonObject.getJSONObject(EMConstants.EMJSONKeys.INFO);
        if (null != subObj) {
            if (!subObj.has(EMConstants.EMJSONKeys.TRIAL_ID) || !subObj.has(EMConstants.EMJSONKeys.TRIAL_NUM)) {
                throw new IncompatibleInputJSONException();
            }
            this.trialId = subObj.getString(EMConstants.EMJSONKeys.TRIAL_ID);
            this.trialNum = subObj.getInt(EMConstants.EMJSONKeys.TRIAL_NUM);
            this.trialResultUrl = subObj.getString(EMConstants.EMJSONKeys.TRIAL_RESULT_URL);
        } else {
            throw new IncompatibleInputJSONException();
        }
    }

    public EMConfigInfo() {
        this.trialNum = EMConstants.EMJSONValueDefaults.TRIAL_NUM_DEFAULT;
        this.trialId = EMConstants.EMJSONValueDefaults.TRIAL_ID_DEFAULT;
        this.trialResultUrl = EMConstants.EMJSONValueDefaults.TRIAL_RESULT_URL_DEFAULT;
    }

    public EMConfigInfo(String trialId, int trialNum, String url) {
        this.trialId = trialId;
        this.trialNum = trialNum;
        this.trialResultUrl = url;
    }

    public JSONObject toJSON() throws EMDataObjectIsInEditingException, EMDataObjectIsNotFilledException {
        if (this.isEditing()) {
            throw new EMDataObjectIsInEditingException();
        }

        if (this.trialId.equalsIgnoreCase(EMConstants.EMJSONValueDefaults.TRIAL_ID_DEFAULT) || this.trialNum == EMConstants.EMJSONValueDefaults.TRIAL_NUM_DEFAULT) {
            throw new EMDataObjectIsNotFilledException();
        }

        JSONObject infoJsonObject = new JSONObject();
        infoJsonObject.put(EMConstants.EMJSONKeys.TRIAL_ID, this.trialId);
        infoJsonObject.put(EMConstants.EMJSONKeys.TRIAL_NUM, this.trialNum);
        infoJsonObject.put(EMConstants.EMJSONKeys.TRIAL_RESULT_URL, this.trialResultUrl);


        return infoJsonObject;
    }

    @Override
    public EMConfigInfo edit() {
        return this;
    }

    @Override
    public EMConfigInfo done() {
        return this;
    }
}
