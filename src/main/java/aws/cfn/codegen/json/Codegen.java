package aws.cfn.codegen.json;

import aws.cfn.codegen.CfnSpecification;
import aws.cfn.codegen.ResourceType;
import aws.cfn.codegen.SingleCfnSpecification;
import aws.cfn.codegen.SpecificationLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private final Config config;

    public Codegen(Config config) throws IOException {
        this.mapper = new ObjectMapper();
        this.definitions = this.mapper.createObjectNode();
        this.config = Objects.requireNonNull(config);
    }

    private CfnSpecification loadSpecification(String region) throws IOException {
        CfnSpecification spec;
        Map<String, URI> regions = config.getSpecifications();
        URI cfnResourceSpecification = regions.get(region);
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

    private Map<String, File> loadGroupsOutputLocation(String region) throws IOException {
        File output = config.getSettings().getOutput();
        Map<String, GroupSpec> groups = config.getGroups();
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

    private String draft() {
        return config.getSettings().getDraft().getLocation();
    }

    private void addToPerGroupRoots(Map<List<String>, ObjectNode> definitions,
                                    Map<String, ObjectNode> groupSpecDefinitions) {
        for (Map.Entry<List<String>, ObjectNode> each: definitions.entrySet()) {
            List<String> key = each.getKey();
            String name = key.get(0);
            String defnName = key.get(1);
            this.config.getGroups().entrySet().stream()
                .filter(e -> e.getValue().isIncluded(name))
                .map(e -> groupSpecDefinitions.get(e.getKey()))
                .forEach(root -> root.replace(defnName, each.getValue()));
        }
    }

    private void generatePerGroup(List<String> definitionNames,
                                  Map<String, File> groupSchemas,
                                  Map<String, ObjectNode> groupSpecDefinitions,
                                  CfnSpecification specification) {

        final Boolean includeIntrinsics = this.config.getSettings().getIncludeIntrinsics();
        final String intrinsics = includeIntrinsics != null && includeIntrinsics ?
            intrinsics() : "";

        groupSpecDefinitions.entrySet().stream()
            // Add resources block to each
            .map(e -> {
                ObjectNode definitions = e.getValue();

                // Add alternative custom resources
                ObjectNode customResource = definitions.putObject("altCustomResource");
                customResource.put("type", "object");
                ObjectNode custProperties = customResource.putObject("properties");
                ObjectNode custType = custProperties.putObject("Type");
                custType.put("type", "string");
                custType.put("pattern", "Custom::[A-Za-z0-9]+");
                custType.put("maxLength", 60);
                ObjectNode custProp = custProperties.putObject("Properties");
                custProp.put("type", "object");
                ArrayNode required = customResource.putArray("required");
                required.add("Type");
                required.add("Properties");
                customResource.put("additionalProperties", false);
                addDependsOn(custProperties);

                ObjectNode resourcesDefnSide = definitions.putObject("resources");
                resourcesDefnSide.put("type", "object");
                resourcesDefnSide.put("additionalProperties", false);
                resourcesDefnSide.put("maxProperties", 200);
                resourcesDefnSide.put("minProperties", 1);
                ObjectNode patternProps = resourcesDefnSide.putObject("patternProperties");
                ObjectNode resourceProps = patternProps.putObject("^[a-zA-Z0-9]{1,255}$");
                ArrayNode anyOf = resourceProps.putArray("oneOf");
                ObjectNode ref = anyOf.addObject();
                ref.put("$ref", "#/definitions/altCustomResource");
                for (String eachDefn: definitionNames) {
                    if (definitions.has(eachDefn)) {
                        ref = anyOf.addObject();
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
                    variables.put("intrinsics", intrinsics);
                    variables.put("resources", res.substring(1, res.length() - 1));
                    String description = "CFN JSON specification generated from version " +
                        specification.getResourceSpecificationVersion();
                    variables.put("description", description);
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

    @SuppressWarnings("unchecked")
    public void generate() throws Exception {
        config.getSettings().getRegions().stream()
            .map(region -> {
                try {
                    return new Object[] {
                        region,
                        loadSpecification(region),
                        loadGroupsOutputLocation(region),
                        loadGroupDefinitions()
                    };
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .forEach(result -> {
                CfnSpecification spec = (CfnSpecification) result[1];
                Map<String, File> locations = (Map<String, File>) result[2];
                Map<String, ObjectNode> defns = (Map<String, ObjectNode>) result[3];
                try {
                    generate(spec, locations, defns);
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    private void generate(CfnSpecification specification,
                          Map<String, File> groupSchemas,
                          Map<String, ObjectNode> groupSpecDefinitions)
        throws Exception {

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
        addToPerGroupRoots(definitions, groupSpecDefinitions);

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
        addToPerGroupRoots(definitions, groupSpecDefinitions);
        generatePerGroup(resDefns, groupSchemas, groupSpecDefinitions, specification);
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

    private void addDependsOn(ObjectNode addTo) {
        ObjectNode dependsOn = addTo.putObject("DependsOn");
        ArrayNode dependsOnTypes = dependsOn.putArray("type");
        dependsOnTypes.add("string");
        dependsOnTypes.add("array");
        ObjectNode items = dependsOn.putObject("items");
        items.put("type", "string");
    }

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
            // Add DependsOn
            addDependsOn(resProps);
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
                    if (propType.isPrimitive()) {
                        addPrimitiveType(each, propType.getPrimitiveType());
                    } else if (propType.isCollectionType()) {
                        each.put("type", "array");
                        ObjectNode itemType = each.putObject("items");
                        if (propType.isContainerInnerTypePrimitive()) {
                            addPrimitiveType(itemType, propType.getPrimitiveItemType());
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
                            addPrimitiveType(patPropKeyValue, propType.getPrimitiveItemType());
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
                }
            }
        );

        if (isResource) {
            if (!required.isEmpty()) {
                ArrayNode array = innerProps.putArray("required");
                required.forEach(array::add);
            }
            innerProps.put("additionalProperties", false);
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
        typeDefn.put("additionalProperties", false);
    }

    private void addPrimitiveType(ObjectNode each, String propType) {
        if (config.getSettings().getDraft() == SchemaDraft.draft07) {
            String type = PrimitiveMappings.get(propType).get();

            if (config.getSettings().getIncludeIntrinsics()) {
                if (!type.equals("string")) {
                    ArrayNode types = each.putArray("anyOf");
                    types.addObject().put("type", type);
                    types.addObject().put("$ref", "#/definitions/Expression");
                } else {
                    each.put("$ref", "#/definitions/Expression");
                }
            } else {
                ArrayNode types = each.putArray("type");
                types.add(type);
                if (!type.equals("object")) {
                    types.add("object");
                }
            }
        }
        else {
            each.put("type",
                PrimitiveMappings.get(propType).get());
        }
    }

    private String intrinsics() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        loader = loader == null ? getClass().getClassLoader() : loader;
        ObjectMapper mapper = new ObjectMapper();
        try {
            InputStream is = loader.getResourceAsStream("Intrinsics.json");
            JsonNode root = mapper.readTree(is);
            String intrinsics = mapper.writer().withDefaultPrettyPrinter().writeValueAsString(root);
            return intrinsics.substring(1, intrinsics.length() - 1).concat(",");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }
}
