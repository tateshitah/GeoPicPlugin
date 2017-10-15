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
		result += "<p><label for=\"parent_page\">Select parent page</label>\n";
		result += "<select id=\"parent_page\" name=\"parent_page\">\n";
		result += "<option value=\"" + parentPages[0] + "\" selected>" + parentPages[0] + "</option>\n";
		for (int i = 1; i < parentPages.length; i++) {
			result += "<option value=\"" + parentPages[i] + "\">" + parentPages[i] + "</option>\n";
		}
		result += "</select>\n</p>";

		return result;
	}

}
