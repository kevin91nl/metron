/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.metron.parsers.contrib.utils;

import org.apache.metron.parsers.contrib.chainlink.ChainLink;
import org.apache.metron.parsers.contrib.common.Constants;
import org.apache.metron.parsers.contrib.links.fields.RenderLink;
import org.apache.metron.parsers.contrib.links.fields.SelectLink;
import org.apache.metron.parsers.contrib.chainlink.ChainLink;
import org.apache.metron.parsers.contrib.common.Constants;
import org.apache.metron.parsers.contrib.links.fields.RenderLink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigUtils {

    public static Map<String, Object> compile(Map<String, Object> config) {
        config = unfoldInput(config);
        return config;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> unfoldInput(Map<String, Object> config) {
        assert config.containsKey("chain");
        assert config.get("chain") instanceof List;
        List links = (List) config.get("chain");

        assert config.containsKey("parsers");
        assert config.get("parsers") instanceof Map;
        Map linksConfig = (Map<String, Object>) config.get("parsers");

        List<String> unfoldedLinks = new ArrayList<>();

        int autolinkIndex = 0;
        for (Object link : links) {
            String linkName = (String) link;
            assert linksConfig.containsKey(linkName);
            assert linksConfig.get(linkName) instanceof Map;
            Map linkConfig = (Map) linksConfig.get(linkName);

            if (linkConfig.containsKey("input")) {
                // Unfold it
                String renderName = Constants.AUTOGENERATED_LINK + autolinkIndex;
                autolinkIndex += 1;
                unfoldedLinks.add(renderName);
                Map<String, Object> renderConfig = new HashMap<>();

                // Figure out which variables are being used to speed up the render link
                String template = (String) linkConfig.get("input");
                List variables = new ArrayList<>();
                Pattern pattern = Pattern.compile("\\{\\{\\s*([^} |.:]+)}");
                Matcher matcher = pattern.matcher(template);
                while (matcher.find()) {
                    String variable = matcher.group(1);
                    variables.add(variable);
                }

                renderConfig.put("class", RenderLink.class.getName());
                renderConfig.put("template", template);
                renderConfig.put("output", Constants.INPUT_MARKER);
                renderConfig.put("variables", variables);
                linksConfig.put(renderName, renderConfig);

                linkConfig.remove("input");
            }

            unfoldedLinks.add(linkName);
        }

        config.put("chain", unfoldedLinks);

        return config;
    }

    @SuppressWarnings("unchecked")
    public static ChainLink getRootLink(Map<String, Object> config) {
        assert config.containsKey("chain");
        assert config.get("chain") instanceof List;
        List links = (List) config.get("chain");

        assert config.containsKey("parsers");
        assert config.get("parsers") instanceof Map;
        Map linksConfig = (Map<String, Object>) config.get("parsers");

        assert links.size() > 0;
        ChainLink prevLink = null;
        ChainLink linkObject = null;
        for (int i = links.size() - 1; i >= 0; i--) {
            assert links.get(i) instanceof String;
            String linkName = (String) links.get(i);
            assert linksConfig.containsKey(linkName);
            assert linksConfig.get(linkName) instanceof Map;
            Map linkConfig = (Map) linksConfig.get(linkName);
            assert linkConfig.containsKey("class");
            assert linkConfig.get("class") instanceof String;
            String className = (String) linkConfig.get("class");
            try {
                linkObject = (ChainLink) ChainLink.class.getClassLoader().loadClass(className).newInstance();
            } catch (InstantiationException e) {
                throw new IllegalStateException("Could not instantiate the following link: " + className);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Illegal access exception for link: " + className);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Class not found exception for link: " + className);
            }
            assert linkObject != null;
            linkConfig.remove("class");
            linkObject.configure(linkConfig);
            if (prevLink != null) {
                linkObject.setNextLink(prevLink);
            }
            prevLink = linkObject;
        }
        assert linkObject != null;
        return linkObject;
    }

}