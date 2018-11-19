package aws.cfn.codegen;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * This defines a CFN's Resource's Property Type.
 *
 * @see <a href="http://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-resource-specification-format.html>Specification</a>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PropertyType extends AttributeType {
    private String documentation;
    private Boolean duplicatesAllowed;
    private String updateType;
    private Boolean required;
}
