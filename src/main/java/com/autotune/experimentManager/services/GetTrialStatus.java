package com.autotune.experimentManager.services;

import com.autotune.experimentManager.data.EMMapper;
import com.autotune.experimentManager.data.ExperimentTrialData;
import com.autotune.experimentManager.utils.EMConstants;
import org.json.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

public class GetTrialStatus extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String inputData = req.getReader().lines().collect(Collectors.joining());
        JSONObject json = new JSONObject(inputData);
        String runId = json.getString(EMConstants.InputJsonKeys.GetTrailStatusInputKeys.RUN_ID);
        String API_RESPONSE = "";
        if (EMMapper.getInstance().getMap().containsKey(runId)) {
            API_RESPONSE = ((ExperimentTrialData) EMMapper.getInstance().getMap().get(runId)).getStatus().toString();
        } else {
            API_RESPONSE = "Invalid Run ID";
        }
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().println(API_RESPONSE);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doGet(req, resp);
    }
}
