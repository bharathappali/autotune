/*******************************************************************************
 * Copyright (c) 2023 Red Hat, IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package com.autotune.analyzer.services;

import com.autotune.analyzer.adapters.RecommendationItemAdapter;
import com.autotune.analyzer.serviceObjects.ListDatasourcesAPIObject;
import com.autotune.analyzer.utils.AnalyzerConstants;
import com.autotune.analyzer.utils.AnalyzerErrorConstants;
import com.autotune.analyzer.utils.GsonUTCDateAdapter;
import com.autotune.common.datasource.DataSourceInfo;
import com.autotune.database.service.ExperimentDBService;
import com.autotune.utils.MetricsConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static com.autotune.analyzer.utils.AnalyzerConstants.ServiceConstants.CHARACTER_ENCODING;
import static com.autotune.analyzer.utils.AnalyzerConstants.ServiceConstants.JSON_CONTENT_TYPE;

/**
 * Rest API used to list DataSources.
 */
@WebServlet(asyncSupported = true)
public class ListDatasources extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListDatasources.class);

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String statusValue = "failure";
        Timer.Sample timerListDS = Timer.start(MetricsConfig.meterRegistry());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding(CHARACTER_ENCODING);
        String gsonStr;

        String dataSourceName = request.getParameter(AnalyzerConstants.ServiceConstants.DATASOURCE_NAME);

        // Key = dataSourceName
        HashMap<String, DataSourceInfo> dataSourceMap = new HashMap<>();
        boolean error = false;

        List<DataSourceInfo> dataSourceInfoList = new ArrayList<>();
        try {

            if (null != dataSourceName) {
                try {
                    DataSourceInfo dataSource = new ExperimentDBService().loadDataSourceFromDBByName(dataSourceName);
                    if (null != dataSource) {
                        dataSourceMap.put(dataSourceName, dataSource);
                    }
                } catch (Exception e) {
                    LOGGER.error("Loading saved Datasource {} failed: {} ", dataSourceName, e.getMessage());
                }

                if (!dataSourceMap.containsKey(dataSourceName)) {
                    error = true;
                    sendErrorResponse(
                            response,
                            new Exception(AnalyzerErrorConstants.APIErrors.ListDataSourcesAPI.INVALID_DATASOURCE_NAME_EXCPTN),
                            HttpServletResponse.SC_BAD_REQUEST,
                            String.format(AnalyzerErrorConstants.APIErrors.ListDataSourcesAPI.INVALID_DATASOURCE_NAME_MSG, dataSourceName)
                    );
                }
                dataSourceInfoList.add(dataSourceMap.get(dataSourceName));
            } else {
                try {
                    dataSourceInfoList = new ExperimentDBService().loadAllDataSources();
                } catch (Exception e) {
                    LOGGER.error("Loading saved Datasources failed: {} ", e.getMessage());
                }
            }

            if (!error) {
                try {
                    ListDatasourcesAPIObject listDatasourcesAPIObject = new ListDatasourcesAPIObject();
                    listDatasourcesAPIObject.setDataSourceInfoList(dataSourceInfoList);

                    // create Gson Object
                    Gson gsonObj = createGsonObject();
                    gsonStr = gsonObj.toJson(listDatasourcesAPIObject);
                    response.getWriter().println(gsonStr);
                    response.getWriter().close();
                    statusValue = "success";

                } catch (Exception e) {
                    LOGGER.error(e.getMessage());
                }
            }

        } catch (Exception e) {
            LOGGER.error("Exception: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
            if (null != timerListDS) {
                MetricsConfig.timerListDS = MetricsConfig.timerBListDS.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerListDS.stop(MetricsConfig.timerListDS);
            }
        }

    }

    public void sendErrorResponse(HttpServletResponse response, Exception e, int httpStatusCode, String errorMsg) throws
            IOException {
        if (null != e) {
            LOGGER.error(e.toString());
            e.printStackTrace();
            if (null == errorMsg) errorMsg = e.getMessage();
        }
        response.sendError(httpStatusCode, errorMsg);
    }
    private Gson createGsonObject() {
        return new GsonBuilder()
                .disableHtmlEscaping()
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
                .registerTypeAdapter(AnalyzerConstants.RecommendationItem.class, new RecommendationItemAdapter())
                .create();
    }

}
