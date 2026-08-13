package com.chua.starter.swagger.support.export;

import com.chua.common.support.lang.document.OpenApiDocumentData;
import com.chua.common.support.lang.document.OpenApiEndpoint;
import com.chua.common.support.lang.document.OpenApiParam;
import com.chua.common.support.lang.document.OpenApiRequestBody;
import com.chua.common.support.lang.document.OpenApiResponse;
import com.chua.common.support.lang.document.OpenApiTag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Swagger / OpenAPI 3.x → 通用 {@link OpenApiDocumentData} 转换器。
 *
 * <p>作为通用 OpenApiDocumentProvider 体系的入参适配层,
 * 与具体协议栈（SpringDoc / native spec / Knife4j）解耦。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class OpenApiConverter {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 将 SpringDoc 收集的 {@link OpenAPI} 转换为通用文档模型。
     */
    public static OpenApiDocumentData fromOpenApi(OpenAPI openAPI) {
        if (openAPI == null) {
            return new OpenApiDocumentData();
        }
        OpenApiDocumentData data = new OpenApiDocumentData();
        if (openAPI.getInfo() != null) {
            data.setTitle(openAPI.getInfo().getTitle());
            data.setVersion(openAPI.getInfo().getVersion());
            data.setDescription(openAPI.getInfo().getDescription());
        }

        // tags
        if (openAPI.getTags() != null) {
            for (Tag t : openAPI.getTags()) {
                OpenApiTag tag = new OpenApiTag();
                tag.setName(t.getName());
                tag.setDescription(t.getDescription());
                data.getTags().add(tag);
            }
        }

        // endpoints
        if (openAPI.getPaths() != null) {
            for (Map.Entry<String, PathItem> e : openAPI.getPaths().entrySet()) {
                String path = e.getKey();
                PathItem item = e.getValue();
                if (item == null) continue;
                eachHttpMethod(path, item.getGet(), "GET", data);
                eachHttpMethod(path, item.getPost(), "POST", data);
                eachHttpMethod(path, item.getPut(), "PUT", data);
                eachHttpMethod(path, item.getDelete(), "DELETE", data);
                eachHttpMethod(path, item.getPatch(), "PATCH", data);
                eachHttpMethod(path, item.getHead(), "HEAD", data);
                eachHttpMethod(path, item.getOptions(), "OPTIONS", data);
            }
        }
        return data;
    }

    private static void eachHttpMethod(String path, Operation op, String method, OpenApiDocumentData data) {
        if (op == null) return;
        OpenApiEndpoint ep = new OpenApiEndpoint();
        ep.setPath(path);
        ep.setMethod(method);
        ep.setSummary(op.getSummary());
        ep.setDescription(op.getDescription());
        ep.setOperationId(op.getOperationId());
        ep.setDeprecated(Boolean.TRUE.equals(op.getDeprecated()));
        if (op.getTags() != null && !op.getTags().isEmpty()) {
            ep.setTag(op.getTags().get(0));
        }

        if (op.getParameters() != null) {
            for (Parameter p : op.getParameters()) {
                OpenApiParam op2 = new OpenApiParam();
                op2.setName(p.getName());
                op2.setIn(p.getIn());
                op2.setType(schemaType(p.getSchema()));
                op2.setRequired(Boolean.TRUE.equals(p.getRequired()));
                op2.setDescription(p.getDescription());
                if (p.getSchema() != null && p.getSchema().getExample() != null) {
                    op2.setExample(String.valueOf(p.getSchema().getExample()));
                }
                ep.getParameters().add(op2);
            }
        }

        if (op.getRequestBody() != null) {
            OpenApiRequestBody body = new OpenApiRequestBody();
            body.setDescription(op.getRequestBody().getDescription());
            body.setRequired(Boolean.TRUE.equals(op.getRequestBody().getRequired()));
            Content content = op.getRequestBody().getContent();
            if (content != null && content.get("application/json") != null) {
                MediaType mt = content.get("application/json");
                body.setType(schemaType(mt.getSchema()));
                Map<String, Object> sample = exampleForSchema(mt.getSchema(), new LinkedHashMap<>());
                try {
                    body.setExample(JSON.writeValueAsString(sample));
                } catch (JsonProcessingException ignored) {
                    body.setExample("{}");
                }
            }
            ep.setRequestBody(body);
        }

        ApiResponses responses = op.getResponses();
        if (responses != null) {
            for (Map.Entry<String, ApiResponse> r : responses.entrySet()) {
                OpenApiResponse or = new OpenApiResponse();
                or.setCode(r.getKey());
                or.setDescription(r.getValue().getDescription());
                if (r.getValue().getContent() != null && r.getValue().getContent().get("application/json") != null) {
                    MediaType mt = r.getValue().getContent().get("application/json");
                    Map<String, Object> flat = flattenSchema(prefix(""), mt.getSchema(), new TreeMap<>());
                    for (Map.Entry<String, Object> f : flat.entrySet()) {
                        OpenApiParam op2 = new OpenApiParam();
                        op2.setName(f.getKey());
                        op2.setType(String.valueOf(f.getValue()));
                        or.getFields().add(op2);
                    }
                    Map<String, Object> sample = exampleForSchema(mt.getSchema(), new LinkedHashMap<>());
                    try {
                        or.setExample(JSON.writeValueAsString(sample));
                    } catch (JsonProcessingException ignored) {
                        or.setExample("{}");
                    }
                }
                ep.getResponses().add(or);
            }
        }

        // 顶层示例
        if (ep.getRequestBody() != null && ep.getRequestBody().getExample() != null) {
            ep.setRequestSample(ep.getRequestBody().getExample());
        }
        if (!ep.getResponses().isEmpty() && ep.getResponses().get(0).getExample() != null) {
            ep.setResponseSample(ep.getResponses().get(0).getExample());
        }

        data.getEndpoints().add(ep);
    }

    private static String prefix(String s) {
        return s == null ? "" : s;
    }

    private static String schemaType(Schema<?> schema) {
        if (schema == null) return "";
        String t = schema.getType();
        if ("array".equals(t) && schema.getItems() != null) {
            return "Array[" + schemaType(schema.getItems()) + "]";
        }
        return t == null ? "" : t;
    }

    private static Map<String, Object> exampleForSchema(Schema<?> schema, Map<String, Object> out) {
        if (schema == null) return out;
        if (schema.getExample() != null) {
            out.put("_example", schema.getExample());
            return out;
        }
        Map<String, Schema> props = schema.getProperties();
        if (props != null) {
            for (Map.Entry<String, Schema> e : props.entrySet()) {
                out.put(e.getKey(), exampleFor(e.getValue()));
            }
        }
        return out;
    }

    private static Object exampleFor(Schema<?> schema) {
        if (schema == null) return null;
        if (schema.getExample() != null) return schema.getExample();
        String t = schema.getType();
        if (t == null) return null;
        return switch (t) {
            case "string" -> "string";
            case "integer", "number" -> 0;
            case "boolean" -> false;
            case "array" -> new ArrayList<>();
            case "object" -> new LinkedHashMap<>();
            default -> null;
        };
    }

    private static Map<String, Object> flattenSchema(String prefix, Schema<?> schema, Map<String, Object> out) {
        if (schema == null) return out;
        if (schema.getProperties() != null) {
            for (Map.Entry<String, Schema> e : schema.getProperties().entrySet()) {
                String name = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
                Schema<?> child = e.getValue();
                if (child.getProperties() != null) {
                    flattenSchema(name, child, out);
                } else {
                    out.put(name, schemaType(child));
                }
            }
        }
        return out;
    }
}
