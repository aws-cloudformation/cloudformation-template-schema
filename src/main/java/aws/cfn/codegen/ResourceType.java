package aws.cfn.codegen;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Defines a resource type from CFN specification file
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode
public class ResourceType {
    private Map<String, AttributeType> attributes = new HashMap<>();
    private String documentation;
    private Map<String, PropertyType> properties = new HashMap<>();
}
