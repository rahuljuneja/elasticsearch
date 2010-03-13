/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.mapper.json;

import com.google.common.collect.ImmutableMap;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.FieldMapperListener;
import org.elasticsearch.index.mapper.MergeMappingException;
import org.elasticsearch.util.json.JsonBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.*;
import static org.elasticsearch.util.MapBuilder.*;

/**
 * @author kimchy (shay.banon)
 */
public class JsonMultiFieldMapper implements JsonMapper {

    public static final String JSON_TYPE = "multi_field";

    public static class Defaults {
        public static final JsonPath.Type PATH_TYPE = JsonPath.Type.FULL;
    }

    public static class Builder extends JsonMapper.Builder<Builder, JsonMultiFieldMapper> {

        private JsonPath.Type pathType = Defaults.PATH_TYPE;

        private final List<JsonMapper.Builder> mappersBuilders = newArrayList();

        private JsonMapper.Builder defaultMapperBuilder;

        public Builder(String name) {
            super(name);
            this.builder = this;
        }

        public Builder pathType(JsonPath.Type pathType) {
            this.pathType = pathType;
            return this;
        }

        public Builder add(JsonMapper.Builder builder) {
            if (builder.name.equals(name)) {
                defaultMapperBuilder = builder;
            } else {
                mappersBuilders.add(builder);
            }
            return this;
        }

        @Override public JsonMultiFieldMapper build(BuilderContext context) {
            JsonPath.Type origPathType = context.path().pathType();
            context.path().pathType(pathType);

            JsonMapper defaultMapper = null;
            if (defaultMapperBuilder != null) {
                defaultMapper = defaultMapperBuilder.build(context);
            }

            context.path().add(name);
            Map<String, JsonMapper> mappers = new HashMap<String, JsonMapper>();
            for (JsonMapper.Builder builder : mappersBuilders) {
                JsonMapper mapper = builder.build(context);
                mappers.put(mapper.name(), mapper);
            }
            context.path().remove();

            context.path().pathType(origPathType);

            return new JsonMultiFieldMapper(name, pathType, mappers, defaultMapper);
        }
    }

    private final String name;

    private final JsonPath.Type pathType;

    private final Object mutex = new Object();

    private volatile ImmutableMap<String, JsonMapper> mappers = ImmutableMap.of();

    private volatile JsonMapper defaultMapper;

    public JsonMultiFieldMapper(String name, JsonPath.Type pathType, JsonMapper defaultMapper) {
        this(name, pathType, new HashMap<String, JsonMapper>(), defaultMapper);
    }

    public JsonMultiFieldMapper(String name, JsonPath.Type pathType, Map<String, JsonMapper> mappers, JsonMapper defaultMapper) {
        this.name = name;
        this.pathType = pathType;
        this.mappers = ImmutableMap.copyOf(mappers);
        this.defaultMapper = defaultMapper;
    }

    @Override public String name() {
        return this.name;
    }

    public JsonPath.Type pathType() {
        return pathType;
    }

    public JsonMapper defaultMapper() {
        return this.defaultMapper;
    }

    public ImmutableMap<String, JsonMapper> mappers() {
        return this.mappers;
    }

    @Override public void parse(JsonParseContext jsonContext) throws IOException {
        JsonPath.Type origPathType = jsonContext.path().pathType();
        jsonContext.path().pathType(pathType);

        // do the default mapper without adding the path
        if (defaultMapper != null) {
            defaultMapper.parse(jsonContext);
        }

        jsonContext.path().add(name);
        for (JsonMapper mapper : mappers.values()) {
            mapper.parse(jsonContext);
        }
        jsonContext.path().remove();

        jsonContext.path().pathType(origPathType);
    }

    @Override public void merge(JsonMapper mergeWith, JsonMergeContext mergeContext) throws MergeMappingException {
        if (!(mergeWith instanceof JsonMultiFieldMapper)) {
            mergeContext.addConflict("Can't merge a non multi_field mapping [" + mergeWith.name() + "] with a multi_field mapping [" + name() + "]");
            return;
        }
        JsonMultiFieldMapper mergeWithMultiField = (JsonMultiFieldMapper) mergeWith;
        synchronized (mutex) {
            // merge the default mapper
            if (defaultMapper == null) {
                if (mergeWithMultiField.defaultMapper != null) {
                    if (!mergeContext.mergeFlags().simulate()) {
                        defaultMapper = mergeWithMultiField.defaultMapper;
                        mergeContext.docMapper().addFieldMapper((FieldMapper) defaultMapper);
                    }
                }
            } else {
                if (mergeWithMultiField.defaultMapper != null) {
                    defaultMapper.merge(mergeWithMultiField.defaultMapper, mergeContext);
                }
            }

            // merge all the other mappers
            for (JsonMapper mergeWithMapper : mergeWithMultiField.mappers.values()) {
                JsonMapper mergeIntoMapper = mappers.get(mergeWithMapper.name());
                if (mergeIntoMapper == null) {
                    // no mapping, simply add it if not simulating
                    if (!mergeContext.mergeFlags().simulate()) {
                        mappers = newMapBuilder(mappers).put(mergeWithMapper.name(), mergeWithMapper).immutableMap();
                        if (mergeWithMapper instanceof JsonFieldMapper) {
                            mergeContext.docMapper().addFieldMapper((FieldMapper) mergeWithMapper);
                        }
                    }
                } else {
                    mergeIntoMapper.merge(mergeWithMapper, mergeContext);
                }
            }
        }
    }

    @Override public void traverse(FieldMapperListener fieldMapperListener) {
        for (JsonMapper mapper : mappers.values()) {
            mapper.traverse(fieldMapperListener);
        }
    }

    @Override public void toJson(JsonBuilder builder, Params params) throws IOException {
        builder.startObject(name);
        builder.field("type", JSON_TYPE);
        builder.field("pathType", pathType.name().toLowerCase());

        builder.startObject("fields");
        if (defaultMapper != null) {
            defaultMapper.toJson(builder, params);
        }
        for (JsonMapper mapper : mappers.values()) {
            mapper.toJson(builder, params);
        }
        builder.endObject();

        builder.endObject();
    }
}