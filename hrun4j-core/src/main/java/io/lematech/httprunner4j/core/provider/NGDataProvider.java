package io.lematech.httprunner4j.core.provider;


import cn.hutool.core.map.MapUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lematech.httprunner4j.common.DefinedException;
import io.lematech.httprunner4j.config.RunnerConfig;
import io.lematech.httprunner4j.core.loader.Searcher;
import io.lematech.httprunner4j.core.loader.TestDataLoaderFactory;
import io.lematech.httprunner4j.entity.testcase.Config;
import io.lematech.httprunner4j.entity.testcase.TestCase;
import io.lematech.httprunner4j.widget.log.MyLog;
import org.testng.collections.Maps;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NGDataProvider {
    private Searcher searcher;

    public NGDataProvider() {
        searcher = new Searcher();
    }

    public Object[][] dataProvider(String pkgName, String testCaseName) {
        String extName = RunnerConfig.getInstance().getTestCaseExtName();
        File dataFilePath = searcher.searchDataFileByRule(pkgName, testCaseName);
        TestCase testCase = TestDataLoaderFactory.getLoader(extName)
                .load(dataFilePath, TestCase.class);
        Object[][] testCases = getObjects(testCase);
        return testCases;
    }

    private Object[][] getObjects(TestCase testCase) {
        Object[][] testCases;
        List<TestCase> result = handleMultiGroupData(testCase);
        testCases = new Object[result.size()][];
        for (int i = 0; i < result.size(); i++) {
            testCases[i] = new Object[]{result.get(i)};
        }
        return testCases;
    }


    /**
     * user_id: [1001, 1002, 1003, 1004]
     * username-password:
     * - ["user1", "111111"]
     * - ["user2", "222222"]
     * - ["user3", "333333"]
     */
    private List<TestCase> handleMultiGroupData(TestCase testCase) {
        ArrayList<TestCase> result = new ArrayList<>();
        Object parameters = testCase.getConfig().getParameters();
        if (parameters == null) {
            result.add(testCase);
            return result;
        }
        if (parameters instanceof Map) {
            parameters = JSONObject.parseObject(JSON.toJSONString(parameters));
        }

        MyLog.debug("class:{}", parameters.getClass());
        if (parameters instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) parameters;
            for (Map.Entry entry : jsonObject.entrySet()) {
                String key = (String) entry.getKey();
                String[] params = key.split("-");
                Object value = entry.getValue();
                if (value instanceof JSONArray) {
                    JSONArray array = (JSONArray) value;
                    for (int i = 0; i < array.size(); i++) {
                        Object arr = array.get(i);
                        ObjectMapper objectMapper = new ObjectMapper();
                        TestCase cpTestCase;
                        try {
                            cpTestCase = objectMapper.readValue(objectMapper.writeValueAsString(testCase), TestCase.class);
                        } catch (JsonProcessingException e) {
                            String exceptionMsg = String.format("testcase deep copy exception : %s", e.getMessage());
                            throw new DefinedException(exceptionMsg);
                        }
                        Map<String, Object> configVariables = (Map) cpTestCase.getConfig().getVariables();
                        Map parameterVariables = Maps.newHashMap();
                        Map resultVariables = Maps.newHashMap();
                        if (params.length == 1) {
                            String name = params[0];
                            parameterVariables.put(name, arr);
                        } else {
                            if (arr instanceof JSONArray) {
                                JSONArray jsonArray = (JSONArray) arr;
                                int size = jsonArray.size();
                                for (int index = 0; index < size; index++) {
                                    String name = params[index];
                                    parameterVariables.put(name, jsonArray.get(index));
                                }
                            }
                        }
                        resultVariables.putAll(MapUtil.isEmpty(configVariables) ? Maps.newHashMap() : configVariables);
                        resultVariables.putAll(MapUtil.isEmpty(parameterVariables) ? Maps.newHashMap() : parameterVariables);
                        Config config = cpTestCase.getConfig();
                        config.setVariables(resultVariables);
                        config.setParameters(parameterVariables);
                        cpTestCase.setConfig(config);
                        result.add(cpTestCase);
                    }
                }
            }
        }
        return result;
    }
}
