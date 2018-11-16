package aws.cfn.codegen;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * Describes the higher level Attributes in CFN.
 * @see <a href="http://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-resource-specification-format.html>Specification</a>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode
public class AttributeType {
    @JsonProperty(required = true)
    private String type;
    private String itemType;
    private String primitiveType;
    private String primitiveItemType;

    public boolean isCollectionType()  {
        return !isPrimitive() && "List".equals(type);
    }

    public boolean isMapType() {
        return !isPrimitive() && "Map".equals(type);
    }

    public boolean isPrimitive() {
        return primitiveType != null || "Json".equals(type);
    }

    public boolean isContainerType() {
        return "List".equals(type) || "Map".equals(type);
    }

    public boolean isObjectType() {
        return !isContainerType() && !isPrimitive() && type != null;
    }


    public boolean isContainerInnerTypePrimitive() {
        return isContainerType() && primitiveItemType != null;
    }

    public Optional<String> getListOrMapPrimitiveType() {
        return Optional.ofNullable(primitiveItemType);
    }

    public Optional<String> getComplexType() {
        // If type is primitive then type == null
        if ("List".equals(type) || "Map".equals(type)) {
            return Optional.ofNullable(itemType);
        }
        // Json shows up in type but CFN calls this primitive type. Let us honor it
        return Optional.ofNullable("Json".equals(type) ? null : type);
    }

    public String getPrimitiveType() {
        return primitiveType;
    }

}
