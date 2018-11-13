package aws.cfn.codegen.json;

import aws.cfn.codegen.CfnSpecification;
import aws.cfn.codegen.ResourceType;
import aws.cfn.codegen.SingleCfnSpecification;
import aws.cfn.codegen.SpecificationLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class Codegen {

    private final ObjectMapper mapper;
    private final ObjectNode definitions;
    private CfnSpecification specification;
    private final Config config;
    private final Map<String, ObjectNode> groupSpecDefinitions;
    private final Map<String, File> groupSchemas;
    public Codegen(Config config) throws IOException {
        this.mapper = new ObjectMapper();
        this.definitions = this.mapper.createObjectNode();
        this.config = Objects.requireNonNull(config);
        this.specification = loadSpecification();
        groupSpecDefinitions = loadGroupDefinitions();
        groupSchemas = loadGroupsOutputLocation();
    }

    private CfnSpecification loadSpecification() throws IOException {
        CfnSpecification spec;
        Map<String, URI> regions = config.getSpecifications();
        URI cfnResourceSpecification = regions.get(config.getSettings().getRegion());
        if (this.config.getSettings().getSingle()) {
            SingleCfnSpecification single = new SpecificationLoader()
                .loadSingleResourceSpecification(
                    cfnResourceSpecification.toURL());
            spec = new CfnSpecification();
            spec.setPropertyTypes(single.getPropertyTypes());
            spec.setResourceTypes(single.getResourceType());
            spec.setResourceSpecificationVersion(single.getResourceSpecificationVersion());
        }
        else {
            spec = new SpecificationLoader()
                .loadSpecification(cfnResourceSpecification.toURL());
        }
        spec.validate();
        return spec;
    }

    private Map<String, File> loadGroupsOutputLocation() throws IOException {
        File output = config.getSettings().getOutput();
        Map<String, GroupSpec> groups = config.getGroups();
        String region = config.getSettings().getRegion();
        if (!output.exists() && !output.mkdirs()) {
            throw new IOException("Can not create out directory to write " + output);
        }

        File parent = new File(output, region);
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Can not create directory for region " + region
                + " at " + parent);
        }

        Map<String, File> groupSchemas = new HashMap<>(groups.size());
        for (Map.Entry<String, GroupSpec> each: groups.entrySet()) {
            File out = new File(parent, each.getKey() + "-spec.json");
            if (!out.exists() && !out.createNewFile()) {
                throw new IOException("Can not create output file to write " + out);
            }
            groupSchemas.put(each.getKey(), out);
        }
        return groupSchemas;
    }

    private Map<String, ObjectNode> loadGroupDefinitions() {
        return config.getGroups().entrySet().stream().
            collect(Collectors.toMap(
                Map.Entry::getKey,
                ign -> mapper.createObjectNode(),
                (first, ign) -> first));
    }

    private String description() {
        return "CFN JSON specification generated from version " +
            specification.getResourceSpecificationVersion();
    }


    private String draft() {
        return config.getSettings().getDraft().getLocation();
    }

    private void addToPerGroupRoots(Map<List<String>, ObjectNode> definitions) {
        for (Map.Entry<List<String>, ObjectNode> each: definitions.entrySet()) {
            List<String> key = each.getKey();
            String name = key.get(0);
            String defnName = key.get(1);
            this.config.getGroups().entrySet().stream()
                .filter(e -> e.getValue().isIncluded(name))
                .map(e -> this.groupSpecDefinitions.get(e.getKey()))
                .forEach(root -> root.replace(defnName, each.getValue()));
        }
    }

    private void generatePerGroup(List<String> definitionNames) {
        groupSpecDefinitions.entrySet().stream()
            // Add resources block to each
            .map(e -> {
                ObjectNode definitions = e.getValue();
                ObjectNode resourcesDefnSide = definitions.putObject("resources");
                resourcesDefnSide.put("type", "object");
                ObjectNode addProps = resourcesDefnSide.putObject("additionalProperties");
                ArrayNode anyOf = addProps.putArray("anyOf");
                for (String eachDefn: definitionNames) {
                    if (definitions.has(eachDefn)) {
                        ObjectNode ref = anyOf.addObject();
                        ref.put("$ref", "#/definitions/" + eachDefn);
                    }
                }
                return e;
            })
            // Write each output file
            .forEach(e -> {
                try {
                    Map<String, Object> variables = new HashMap<>(5);
                    variables.put("draft", draft());
                    String res =
                        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(e.getValue());
                    variables.put("resources", res.substring(1, res.length() - 1));
                    variables.put("description", description());
                    Mustache cfnSchema = new DefaultMustacheFactory().compile("Schema.template");
                    cfnSchema.execute(new OutputStreamWriter(
                        new FileOutputStream(groupSchemas.get(e.getKey())),
                        StandardCharsets.UTF_8
                    ), variables).flush();
                }
                catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
    }

    public void generate() throws Exception {
        final Map<String, ResourceType> resources = specification.getResourceTypes();
        final Map<String, ResourceType> properties = specification.getPropertyTypes();
        final Set<String> propertyNames = properties.keySet();
        final List<String> resDefns = new ArrayList<>(resources.size());
        List<String> sorted= new ArrayList<>(resources.keySet());
        Collections.sort(sorted);

        Map<List<String>, ObjectNode> definitions = new LinkedHashMap<>(sorted.size());
        for (final String name: sorted) {
            ResourceType type = resources.get(name);
            String defnName = name.replace("::", "_");
            resDefns.add(defnName);
            ObjectNode typeDefn = mapper.createObjectNode();
            handleType(typeDefn, defnName, name, type, true, propertyNames);
            definitions.put(Arrays.asList(name, defnName), typeDefn);
        }
        addToPerGroupRoots(definitions);

        sorted = new ArrayList<>(properties.keySet());
        Collections.sort(sorted);
        definitions = new LinkedHashMap<>(sorted.size());
        for (final String name: sorted) {
            ResourceType type = properties.get(name);
            String[] parts = name.split("\\.");
            if (parts.length > 1) {
                String defnName = parts[0].replace("::", "_");
                String propName = parts[1];
                ObjectNode typeDefn = mapper.createObjectNode();
                handleType(typeDefn, defnName, propName, type, false, propertyNames);
                List<String> key = Arrays.asList(name, defnName + "_" + propName);
                definitions.put(key, typeDefn);
            }
            else {
                // equals 1, no namespacing case
                String defnName = name.replace("::", "_");
                ObjectNode typeDefn = mapper.createObjectNode();
                handleType(typeDefn, defnName, defnName, type, false, propertyNames);
                List<String> key = Arrays.asList(name, defnName);
                definitions.put(key, typeDefn);
            }

        }
        addToPerGroupRoots(definitions);

        generatePerGroup(resDefns);
        /*
        // Add Resources definition section
        ObjectNode resourcesDefnSide = this.definitions.putObject("resources");
        resourcesDefnSide.put("type", "object");
        ObjectNode addProps = resourcesDefnSide.putObject("additionalProperties");
        ArrayNode anyOf = addProps.putArray("anyOf");
        // Collections.sort(resDefns);
        resDefns.forEach(defn -> {
            ObjectNode ref = anyOf.addObject();
            ref.put("$ref", "#/definitions/" + defn);
        });

        Mustache cfnSchema = new DefaultMustacheFactory().compile("Schema.template");
        cfnSchema.execute(new OutputStreamWriter(
            new FileOutputStream(output),
            StandardCharsets.UTF_8
        ), this).flush();
        */

    }

    private final static Map<String, Supplier<String>> PrimitiveMappings =
        new HashMap<String, Supplier<String>>() {{
            put("String", () -> "string");
            put("Number", () -> "integer");
            put("Integer", () -> "integer");
            put("Float", () -> "number");
            put("Double", () -> "number");
            put("Long", () -> "integer");
            put("Json", () -> "object");
            put("Boolean", () -> "boolean");
            put("Timestamp", () -> "string");

        }};

    private void handleType(ObjectNode typeDefn,
                            String defnName,
                            String name,
                            ResourceType type,
                            boolean isResource,
                            Set<String> propertyNames) {
        typeDefn.put("type", "object");
        typeDefn.put("description", type.getDocumentation());
        ObjectNode properties, innerProps = null;
        if (isResource) {
            ObjectNode resProps = typeDefn.putObject("properties");
            ObjectNode enumType = resProps.putObject("Type");
            enumType.put("description", "Type of resource equals only " + name);
            enumType.put("type", "string");
            ArrayNode array = enumType.putArray("enum");
            array.add(name);
            innerProps = resProps.putObject("Properties");
            innerProps.put("type", "object");
            properties = innerProps.putObject("properties");
        }
        else {
            properties = typeDefn.putObject("properties");
        }
        final List<String> required = new ArrayList<>(5);
        type.getProperties().forEach(
            (propName, propType) -> {
                ObjectNode each = properties.putObject(propName);
                if (propType.isObjectType()) {
                    each.put("$ref", "#/definitions/" +
                        (propertyNames.contains(propType.getType()) ? propType.getType() :
                         defnName + "_" + propType.getType()));
                }
                else {
                    each.put("description", propType.getDocumentation());
                    ObjectNode oldEach = each;
                    // ArrayNode oneOf = each.putArray("oneOf");
                    // each = oneOf.insertObject(0);
                    if (propType.isPrimitive()) {
                        each.put("type",
                            PrimitiveMappings.get(propType.getPrimitiveType()).get());
                    } else if (propType.isCollectionType()) {
                        each.put("type", "array");
                        ObjectNode itemType = each.putObject("items");
                        if (propType.isContainerInnerTypePrimitive()) {
                            itemType.put("type",
                                PrimitiveMappings.get(propType.getPrimitiveItemType()).get());
                        } else {
                            itemType.put("$ref", "#/definitions/" +
                                (propertyNames.contains(propType.getItemType()) ? propType.getItemType() :
                                    defnName + "_" + propType.getItemType()));
                        }
                        Boolean duplicates = propType.getDuplicatesAllowed();
                        if (duplicates != null && !duplicates) {
                            each.put("uniqueItems", true);
                        }
                        each.put("minItems", 0);
                    } else {
                        // Map Type
                        each.put("type", "object");
                        ObjectNode mapProps = each.putObject("patternProperties");
                        ObjectNode patPropKeyValue = mapProps.putObject("[a-zA-Z0-9]+");
                        if (propType.isContainerInnerTypePrimitive()) {
                            patPropKeyValue.put("type",
                                PrimitiveMappings.get(propType.getPrimitiveItemType()).get());
                        } else {
                            patPropKeyValue.put("$ref", "#/definitions/" +
                                (propertyNames.contains(propType.getItemType()) ? propType.getItemType() :
                                    defnName + "_" + propType.getItemType()));
                        }
                    }
                    Boolean requiredB = propType.getRequired();
                    if (requiredB != null && requiredB) {
                        required.add(propName);
                    }

                    //
                    // { "$ref": "#/definitions/FnRef" },
                    // { "$ref": "#/definitions/FnGetAtt" },
                    // { "type": "object" }
                    //
                    // each = oneOf.insertObject(1);
                    // each.put("$ref", "#/definitions/FnRef");
                    // each = oneOf.insertObject(2);
                    // each.put("$ref", "#/definitions/FnGetAtt");
                    // each = oneOf.insertObject(2);
                    // each.put("type", "object");
                }
            }
        );

        if (isResource) {
            if (!required.isEmpty()) {
                ArrayNode array = innerProps.putArray("required");
                required.forEach(array::add);
            }
            ArrayNode array = typeDefn.putArray("required");
            array.add("Type");
            if (!required.isEmpty()) {
                array.add("Properties");
            }
        }
        else {
            if (!required.isEmpty()) {
                ArrayNode array = typeDefn.putArray("required");
                required.forEach(array::add);
            }
        }
    }
}
