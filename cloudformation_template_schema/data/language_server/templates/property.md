{% if prop.description %}{{ prop.description }}{% endif %}

---

Required: {% if prop.___IsRequired %}{{ prop.___IsRequired }}{% else %}No{% endif %}  {% if prop.type != 'object' %}
Type: {{ prop.type|capitalize }}  {% endif %}{% if prop.enum %}
Allowed Values: {% for allowedvalue in prop.enum %}{{ allowedvalue }}{% if not loop.last %} | {% endif %}{% endfor %}  {% endif %}{% if prop.minLength %}
Minimum Length: {{ prop.minLength }}  {% endif %}{% if prop.maxLength %}
Maximum Length: {{ prop.maxLength }}  {% endif %}{% if prop.pattern %}
Pattern: {{ prop.pattern }}  {% endif %}{% if prop.___CreateOnly %}
Update requires: Replacement{% elif prop.___Conditional %}
Update requires: Some interruptions{% else %}
Update requires: No interruption{% endif %}
{% if prop.___ReadOnlyProperty %}Read only property: Yes{% endif %}
