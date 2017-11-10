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
		// result += "\t<label for=\"location\">Location:</label>\n";
		// result += "\t<input type=\"text\" name=\"location\" id=\"location\"
		// size=\"40\" /><br/>\n";
		result += "\t<label for=\"lat\">Latitude:</label>\n";
		result += "\t<input type=\"number\" name=\"lat\" id=\"lat\" size=\"15\" min=\"-90.0\" max=\"90.0\" step=\"0.0000000001\" required />\n";
		result += "\t<label for=\"lon\">Longitude:</label>\n";
		result += "\t<input type=\"number\" name=\"lon\" id=\"lon\" size=\"15\" min=\"-180.0\" max=\"180.0\" step=\"0.0000000001\" required />\n";
		result += "\t<button type=\"button\" onclick=\"getLocation()\">Get Location</button>\n";
		result += "\t</p>\n\t<script>\n";
		result += "\tfunction getLocation() {\n";
		result += "\t\tif (navigator.geolocation) {\n";
		result += "\t\t\tnavigator.geolocation.watchPosition(update);}}\n";

		result += "\tfunction update(position) {\n";
		result += "\t\tvar lat = position.coords.latitude;\n";
		result += "\t\tvar lng = position.coords.longitude;\n";
		// result += "\t\tdocument.getElementById('location').value = lat + ', ' +
		// lng;\n";
		result += "\t\tdocument.getElementById('lat').value = lat;\n";
		result += "\t\tdocument.getElementById('lon').value = lng;}\n";
		result += "\t</script>\n";
		result += "\t<p><label for=\"description\">Description:</label><br/>\n";
		result += "\t<textarea name=\"description\" id=\"description\" rows=\"3\" cols=\"60\" ></textarea></p>\n";
		result += "\t<p>\n\t<input type=\"submit\" value=\"Upload\">\n";
		result += "\t<input type=\"reset\" value=\"Reset\">\n\t</p>\n";

		result += "</form>\n";

		// for Debug
		// WikiEngine engine = context.getEngine();
		// result += engine.getWorkDir() + ": " + engine.toString();

		return result;
	}

}
