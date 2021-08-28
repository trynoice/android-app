# Contributors

## Code

The following list is auto-generated from the Git history, and it is prone to
duplicates.

{% assign authorsByLetter = site.data.authors.code | group_by_exp: "author", "author[0] | split: '' | first | upcase" -%}
|   #   | Name |
| :---: | ---- |

{%- for letter in authorsByLetter %}
{%- for author in letter.items %}
| {%- if currentLetter != letter.name -%}{{ letter.name }}{%- endif -%}
| [{{ author[0] | escape_once }}]({{ author[1] }}) |
{%- assign currentLetter = letter.name -%}
{%- endfor %}
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
