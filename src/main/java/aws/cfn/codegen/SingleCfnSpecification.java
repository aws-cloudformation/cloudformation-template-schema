package aws.cfn.codegen;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode
@ToString
public class SingleCfnSpecification {
    private String resourceSpecificationVersion;
    private Map<String, ResourceType> propertyTypes = new HashMap<>(256);
    private Map<String, ResourceType> resourceType;

    public void validate() throws CfnSpecificationException {
        resourceType.values().iterator().next().getProperties().forEach(
            (name, type) -> {
                type.getComplexType().ifPresent(propName -> {
                    if (!propertyTypes.containsKey(propName)) {
                        throw new CfnSpecificationException(
                            "Property name " + name + " referenced but not defined " +
                                " Names = " + propertyTypes.keySet());
                    }
                });
            });
    }
}
