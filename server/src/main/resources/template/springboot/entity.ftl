package ${packageName};

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;
import lombok.Data;
<#if validation??>
import javax.validation.constraints.*;
</#if>

@Data
@NoArgsConstructor
public class ${className} {

    <#list columns as col>
        private ${col.className} ${camel('project_id')};
    </#list>

}

