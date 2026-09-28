"""Derive query-result.jrxml from student-list.jrxml.

The masthead, footer and signatory bands are the official layout and must be
identical to every other landscape report, so they are inherited wholesale
rather than rewritten. Only the parts that describe *this* report's columns are
removed: the field declarations, the column header cells and the detail cells.
DynamicTableReportService puts those back at run time, once it knows what the
query returned.
"""
import re
from pathlib import Path

REPORTS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "reports"
src = (REPORTS / "student-list.jrxml").read_text(encoding="utf-8")

out = src.replace('name="student-list"', 'name="query-result"')

# The query and its scope note ride above the table, so a reader can see what
# produced the rows rather than having to trust the title. Declared with the
# other parameters: the DTD wants every parameter before the first field.
out = out.replace('  <!-- FIELDS -->', '''  <parameter name="QUERY_SUMMARY" class="java.lang.String"><defaultValueExpression><![CDATA[""]]></defaultValueExpression></parameter>
  <parameter name="QUERY_NOTE" class="java.lang.String"><defaultValueExpression><![CDATA[""]]></defaultValueExpression></parameter>

  <!-- FIELDS -->''')

# Fields: the running query decides these.
out = re.sub(r'[ \t]*<field name="[^"]+"[^/]*/>\n', '', out)
out = out.replace('  <!-- FIELDS -->', '  <!-- FIELDS -->\n  <!-- @FIELDS@ -->')

# ROW_NUMBER counted a field that no longer exists. REPORT_COUNT is built in
# and counts detail rows regardless of what they contain.
out = re.sub(
    r'[ \t]*<variable name="ROW_NUMBER".*?</variable>\n',
    '', out, flags=re.DOTALL)
out = out.replace('$V{ROW_NUMBER}', '$V{REPORT_COUNT}')

# Column header and detail depend on what the query returned, so they are left
# as markers. DynamicTableReportService substitutes generated JRXML for them —
# generated rather than built through the design API because runDirection, which
# is what makes Arabic render right-to-left, has no setter in 6.21.3 and the
# bands must match the hand-written reports exactly.
out = re.sub(r'<columnHeader>.*?</columnHeader>', '<!-- @COLUMN_HEADER@ -->', out, flags=re.DOTALL)
out = re.sub(r'<detail>.*?</detail>', '<!-- @DETAIL@ -->', out, flags=re.DOTALL)

# Printed once, above the table. printWhenExpression keeps the box off the page
# entirely when there is nothing to say, rather than leaving an empty frame.
out = out.replace('  <!-- @COLUMN_HEADER@ -->', '''  <title>
    <band height="40" splitType="Stretch">
      <rectangle>
        <reportElement x="0" y="2" width="802" height="34" backcolor="#f7f2f3" forecolor="#e8c0c8"/>
        <graphicElement><pen lineWidth="0.5"/></graphicElement>
      </rectangle>
      <textField textAdjust="StretchHeight">
        <reportElement x="8" y="4" width="786" height="15"/>
        <textElement textAlignment="Center" verticalAlignment="Middle"
                     runDirection="$P{IS_RTL} ? &quot;RTL&quot; : &quot;LTR&quot;">
          <font fontName="Cairo" size="8" isBold="true"/>
        </textElement>
        <textFieldExpression><![CDATA[$P{QUERY_SUMMARY}]]></textFieldExpression>
      </textField>
      <textField textAdjust="StretchHeight">
        <reportElement x="8" y="19" width="786" height="15" forecolor="#722030">
          <printWhenExpression><![CDATA[$P{QUERY_NOTE} != null && $P{QUERY_NOTE}.length() > 0]]></printWhenExpression>
        </reportElement>
        <textElement textAlignment="Center" verticalAlignment="Middle"
                     runDirection="$P{IS_RTL} ? &quot;RTL&quot; : &quot;LTR&quot;">
          <font fontName="Cairo" size="7" isItalic="true"/>
        </textElement>
        <textFieldExpression><![CDATA[$P{QUERY_NOTE}]]></textFieldExpression>
      </textField>
    </band>
  </title>

  <!-- @COLUMN_HEADER@ -->''')

dest = REPORTS / "query-result.jrxml"
dest.write_text(out, encoding="utf-8")
print(f"wrote {dest} ({len(out.splitlines())} lines)")
for tag in ("<field", "ROW_NUMBER", "@COLUMN_HEADER@", "@DETAIL@", "@FIELDS@", "QUERY_SUMMARY"):
    print(f"  {tag}: {out.count(tag)}")
