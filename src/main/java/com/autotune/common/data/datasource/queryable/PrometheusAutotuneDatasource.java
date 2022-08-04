package com.autotune.common.data.datasource.queryable;

import com.autotune.common.data.datasource.AutotuneDatasourceServiceability;
import com.autotune.common.experiments.DatasourceInfo;
import com.autotune.common.utils.CommonConstants;
import com.autotune.common.utils.CommonUtils;

public class PrometheusAutotuneDatasource extends QueryableAutotuneDatasource {
    private String name;
    private String source;

    public PrometheusAutotuneDatasource() {
        this.name = CommonConstants.AutotuneDatasource.Prometheus.DEFAULT_NAME;
        this.source = null;
    }

    public PrometheusAutotuneDatasource(DatasourceInfo datasourceInfo) {
        this.name = datasourceInfo.getName();
        this.source = datasourceInfo.getUrl().toString();
    }

    /**
     * Get the name of the datasource
     * @return the name of the datasource
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the datasource
     * @param name Name of the datasource
     */
    @Override
    public void setName(String name) {
        if((null != name) && (0 != name.trim().length()))
            this.name = name;
    }

    /**
     * Needs a check for null when used
     * @return the source variable
     */
    @Override
    public String getSource() {
        return this.source;
    }

    /**
     * Sets the source of the datasource
     * @param source
     */
    @Override
    public void setSource(String source) {
        if((null != source) && (0 != source.trim().length()))
            this.source = source;
    }

    /**
     *
     * @return
     */
    @Override
    public CommonUtils.DatasourceReachabilityStatus isReachable() {
        if((null != source) && (0 != source.trim().length()))
            return CommonUtils.DatasourceReachabilityStatus.SOURCE_NOT_SET;
        /**
         * Implementation needed for reachability
         */
        return CommonUtils.DatasourceReachabilityStatus.NOT_REACHABLE;
    }

    @Override
    public CommonUtils.DatasourceReliabilityStatus isReliable(Object... objects) {
        /**
         * Needs appropriate implementation
         */
        return CommonUtils.DatasourceReliabilityStatus.NOT_RELIABLE;
    }
}
