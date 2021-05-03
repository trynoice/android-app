# Contributors

## Code

The following list is auto-generated from the Git history, and it is prone to
duplicates.

| Author | Email |
| :----- | :---: |

{%- for author in site.data.authors.code %}
| {{ author | escape_once }} |
{%- endfor %}

## Sounds

| Attribution | Source |
| :---------- | :----: |

{%- for sound in site.data.authors.sounds %}
| {{ sound.description | escape_once }} | [{{ sound.source | escape_once }}]({{ sound.url }}) |
{%- endfor %}

## Icons

| Attribution | Source |
| :---------- | :----: |

{%- for icon in site.data.authors.icons %}
| {{ icon.description | escape_once }} | [{{ icon.source | escape_once }}]({{ icon.url }}) |
{%- endfor %}
