package com.autotune.analyzer.services;

import com.autotune.analyzer.AutotuneExperiment;
import com.autotune.analyzer.Experimentator;
import com.autotune.analyzer.utils.ExperimentHelpers;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.autotune.analyzer.utils.AnalyzerConstants.ServiceConstants.CHARACTER_ENCODING;
import static com.autotune.analyzer.utils.AnalyzerConstants.ServiceConstants.JSON_CONTENT_TYPE;

public class ExperimentsSummary extends HttpServlet
{
	@Override
	protected void doGet(HttpServletRequest request,
						 HttpServletResponse response) throws ServletException, IOException {
		try {
			response.setStatus(HttpServletResponse.SC_OK);
			response.setContentType(JSON_CONTENT_TYPE);
			response.setCharacterEncoding(CHARACTER_ENCODING);

			JSONArray experimentsJSONArray = new JSONArray();
			for (String experimentID : Experimentator.experimentsMap.keySet()) {
				AutotuneExperiment autotuneExperiment = Experimentator.experimentsMap.get(experimentID);
				JSONObject experimentJSON = ExperimentHelpers.experimentToJSON(autotuneExperiment);
				experimentsJSONArray.put(experimentJSON);
			}
			response.getWriter().println(experimentsJSONArray.toString(4));
			response.getWriter().close();
		} catch (Exception ex) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
}
