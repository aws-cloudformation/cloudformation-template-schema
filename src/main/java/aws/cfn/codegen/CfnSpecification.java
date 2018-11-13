package aws.cfn.codegen;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.AbstractCollection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A simple class the represents CFN resource specification. Properties defined within CFN specification
 * are also defined exactly like a Resource.
 *
 * @see <a href="https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-resource-specification-format.html>CFN Spec</a>
 */
@Data
@EqualsAndHashCode
@ToString
public class CfnSpecification {
    private String resourceSpecificationVersion;
    private Map<String, ResourceType> propertyTypes = new HashMap<>(256);
    private Map<String, ResourceType> resourceTypes = new HashMap<>(256);

    public void validate() throws CfnSpecificationException {
        resourceTypes.forEach(
            (name, type) -> {
                type.getProperties().forEach(
                    (propName, propType) -> {
                        propType.getComplexType().ifPresent(cplx -> {
                            if (!propertyTypes.containsKey(name + "." + cplx) &&
                                !propertyTypes.containsKey(cplx)) {
                                throw new CfnSpecificationException(cplx + " referenced but not defined in " + name);
                            }
                        });
                    }
                );
            }
        );
    }

    @lombok.Data
    @lombok.ToString
    @lombok.EqualsAndHashCode
    public static class Difference {
        private String fromVersion;
        private ResourceType fromType;
        private String toVersion;
        private ResourceType toType;
    }

    public Set<Difference> findDiff(CfnSpecification toSpec) {
        return resourceTypes.entrySet().stream()
            .filter(entry -> toSpec.resourceTypes.containsKey(entry.getKey()))
            .collect(
                HashSet::new,
                (diffSet, e) -> {
                    if (!toSpec.getResourceTypes().get(e.getKey()).equals(e)) {
                        Difference diff = new Difference();
                        diff.setFromType(e.getValue());
                        diff.setFromVersion(resourceSpecificationVersion);
                        diff.setToType(toSpec.getResourceTypes().get(e.getKey()));
                        diff.setToVersion(toSpec.getResourceSpecificationVersion());
                        diffSet.add(diff);
                    }
                },
                AbstractCollection::addAll);
    }
}
