/*

Copyright (c) 2017 Hiroaki Tateshita

Permission is hereby granted, free of charge, to any person obtaining a copy 
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell 
copies of the Software, and to permit persons to whom the Software is furnished
to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all 
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A 
PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT 
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION 
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE 
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

this source code includes the work that is distributed in the Apache License 2.0
http://www.apache.org/licenses/LICENSE-2.0

 */

package org.braincopy.jspwiki.plugin;

import java.util.Map;

import org.apache.wiki.WikiContext;
import org.apache.wiki.api.exceptions.PluginException;
import org.apache.wiki.api.plugin.WikiPlugin;

/**
 * @author Hiroaki Tateshita
 *
 */
public class GeoPicPlugin implements WikiPlugin {

	public String execute(WikiContext context, Map<String, String> params) throws PluginException {
		String result = "";

		String[] parentPages = null;
		if (params != null) {
			String tempStr = null;
			tempStr = params.get("pages");
			if (tempStr != null) {
				parentPages = tempStr.split("/");
			}
		}
		if (parentPages == null) {
			parentPages = new String[1];
			parentPages[0] = context.getName();
		}
		result += "<form action=\"gpupload\" enctype=\"multipart/form-data\" method=\"post\">\n";

		// select parent
		result += "\t<p><label for=\"parent_page\">Select parent page</label>\n";
		result += "\t<select id=\"parent_page\" name=\"parent_page\">\n";
		result += "\t\t<option value=\"" + parentPages[0] + "\" selected>" + parentPages[0] + "</option>\n";
		for (int i = 1; i < parentPages.length; i++) {
			result += "\t\t<option value=\"" + parentPages[i] + "\">" + parentPages[i] + "</option>\n";
		}
		result += "\t</select>\n\t</p>\n";

		result += "\t<p>\n\t<label for=\"name\">Name:</label>\n";
		result += "\t<input type=\"text\" name=\"name\" id=\"name\" size=\"40\" required /></p>\n";
		result += "\t<p>\n\t<label for=\"attachfilename\">Select file:</label>\n";
		result += "\t<input type=\"file\" name=\"content\" id=\"attachfilename\" size=\"60\" required />\n";
		result += "\t</p>\n\t<p>";

		// for EXIF
		result += "\t<p><label for=\"useExif\">Exif:</label> \n";
		result += "\t<input type=\"checkbox\" name=\"useExif\" id=\"useExif\" value=\"1\"";
		result += "onclick=\"setEditEnable(this.checked)\" > I use Exif\n";
		//result += "\t<input type=\"checkbox\" name=\"deleteExif\" id=\"deleteExif\" value=\"2\"";
		//result += " > I delete Exif";
		result += "</p>\n";

		// for lat lon
		result += "\t<label for=\"lat\">Latitude:</label>\n";
		result += "\t<input type=\"number\" name=\"lat\" id=\"lat\" size=\"15\" min=\"-90.0\" max=\"90.0\" step=\"0.0000000001\" />\n";
		result += "\t<label for=\"lon\">Longitude:</label>\n";
		result += "\t<input type=\"number\" name=\"lon\" id=\"lon\" size=\"15\" min=\"-180.0\" max=\"180.0\" step=\"0.0000000001\" />\n";
		result += "\t<button type=\"button\" onclick=\"getLocation()\">Get Location</button>\n";
		result += "\t</p>\n\t<script>\n";
		result += "\tfunction getLocation() {\n";
		result += "\t\tif (navigator.geolocation) {\n";
		result += "\t\t\tnavigator.geolocation.watchPosition(update);}}\n";
		result += "\tfunction update(position) {\n";
		result += "\t\tvar lat = position.coords.latitude;\n";
		result += "\t\tvar lng = position.coords.longitude;\n";
		result += "\t\tdocument.getElementById('lat').value = lat;\n";
		result += "\t\tdocument.getElementById('lon').value = lng;}\n";
		result += "\t</script>\n";


		// for description
		result += "\t<p><label for=\"description\">Description:</label><br/>\n";
		result += "\t<textarea name=\"description\" id=\"description\" rows=\"3\" cols=\"60\" ></textarea></p>\n";
		result += "\t<p>\n\t<input type=\"submit\" value=\"Upload\">\n";
		result += "\t<input type=\"reset\" value=\"Reset\">\n\t</p>\n";
		result += "\t<input type=\"hidden\" name=\"page\" value=\"";
		result += context.getName() + "\" />\n";
		result += "\t</p>\n";

		result += "\t<script>\n";
		result += "\tfunction setEditEnable(boolEnable){\n";
		result += "\t\tvar lat_elm = document.getElementById('lat');\n";
		result += "\t\tlat_elm.disabled = boolEnable;\n";
		result += "\t\tvar lon_elm = document.getElementById('lon');\n";
		result += "\t\tlon_elm.disabled = boolEnable;\n";
		result += "\t}\n";
		result += "\t</script>\n";

		result += "</form>\n";

		return result;
	}

}
