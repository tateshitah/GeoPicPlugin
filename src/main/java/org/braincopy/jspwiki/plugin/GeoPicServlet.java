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

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.security.Permission;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.wiki.PageManager;
import org.apache.wiki.WikiContext;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.api.exceptions.PluginException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.api.exceptions.RedirectException;
import org.apache.wiki.attachment.AttachmentServlet;
import org.apache.wiki.auth.AuthorizationManager;
import org.apache.wiki.auth.permissions.PermissionFactory;
import org.apache.wiki.parser.PluginContent;
import org.apache.wiki.ui.progress.ProgressItem;
import org.apache.wiki.util.TextUtil;

@WebServlet("/gpupload")
public class GeoPicServlet extends AttachmentServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private WikiEngine m_engine;

	/**
	 * The maximum size that an attachment can be.
	 */
	private int m_maxSize = Integer.MAX_VALUE;

	private static final Logger log = Logger.getLogger(AttachmentServlet.class);

	@Override
	public void init(ServletConfig config) throws ServletException {
		m_engine = WikiEngine.getInstance(config);
		super.init(config);
	}

	/**
	 * Uploads a specific mime multipart input set, intercepts exceptions.
	 *
	 * @param req
	 *            The servlet request
	 * @return The page to which we should go next.
	 * @throws RedirectException
	 *             If there's an error and a redirection is needed
	 * @throws IOException
	 *             If upload fails
	 * @throws FileUploadException
	 */
	@Override
	protected String upload(HttpServletRequest req) throws RedirectException, IOException {

		/////
		String msg = "";
		String attName = "(unknown)";

		// If something bad happened, Upload should be able to take care of most
		// stuff
		String errorPage = m_engine.getURL(WikiContext.ERROR, "", null, false);
		String nextPage = errorPage;

		String progressId = req.getParameter("progressid");

		// Check that we have a file upload request
		if (!ServletFileUpload.isMultipartContent(req)) {
			throw new RedirectException("Not a file upload", errorPage);
		}

		try {
			FileItemFactory factory = new DiskFileItemFactory();

			// Create the context _before_ Multipart operations, otherwise
			// strict servlet containers may fail when setting encoding.
			WikiContext context = m_engine.createContext(req, WikiContext.ATTACH);

			UploadListener pl = new UploadListener();

			m_engine.getProgressManager().startProgress(pl, progressId);

			ServletFileUpload upload = new ServletFileUpload(factory);
			upload.setHeaderEncoding("UTF-8");
			if (!context.hasAdminPermissions()) {
				upload.setFileSizeMax(m_maxSize);
			}
			upload.setProgressListener(pl);
			List<FileItem> items = upload.parseRequest(req);

			String wikipage = null;
			String changeNote = null;
			FileItem actualFile = null;

			///// added by Hiroaki Tateshita
			String name = null;
			String parent = null;
			String temp = null;
			float lat = Float.MAX_VALUE;
			float lon = Float.MAX_VALUE;
			String description = null;
			/////

			for (FileItem item : items) {
				if (item.isFormField()) {
					if (item.getFieldName().equals("page")) {
						//
						// FIXME: Kludge alert. We must end up with the parent
						// page name,
						// if this is an upload of a new revision
						//

						wikipage = item.getString("UTF-8");
						int x = wikipage.indexOf("/");

						if (x != -1)
							wikipage = wikipage.substring(0, x);
					} else if (item.getFieldName().equals("changenote")) {
						changeNote = item.getString("UTF-8");
						if (changeNote != null) {
							changeNote = TextUtil.replaceEntities(changeNote);
						}
					} else if (item.getFieldName().equals("nextpage")) {
						nextPage = validateNextPage(item.getString("UTF-8"), errorPage);
					}

					///// added by Hiroaki Tateshita
					else if (item.getFieldName().equals("name")) {
						name = item.getString("UTF-8");
						if (name == "") {
							throw new RedirectException("Wrong page name", errorPage);
						}
					} else if (item.getFieldName().equals("parent_page")) {
						parent = item.getString("UTF-8");
						if (!m_engine.pageExists(parent)) {
							throw new RedirectException("Wrong parent page name", errorPage);
						}

					} else if (item.getFieldName().equals("lat")) {
						temp = item.getString("UTF-8");
						if (!temp.equals("")) {
							lat = Float.parseFloat(temp);
						}
					} else if (item.getFieldName().equals("lon")) {
						temp = item.getString("UTF-8");
						if (!temp.equals("")) {
							lon = Float.parseFloat(temp);
						}
					} else if (item.getFieldName().equals("description")) {
						description = item.getString("UTF-8");
					} /////

				} else {
					actualFile = item;
				}
			}

			if (actualFile == null)
				throw new RedirectException("Broken file upload", errorPage);

			//
			// FIXME: Unfortunately, with Apache fileupload we will get the form
			// fields in
			// order. This means that we have to gather all the metadata from
			// the
			// request prior to actually touching the uploaded file itself. This
			// is because the changenote appears after the file upload box, and
			// we
			// would not have this information when uploading. This also means
			// that with current structure we can only support a single file
			// upload
			// at a time.
			//
			String filename = actualFile.getName();

			///// added by Hiroaki Tateshita

			AuthorizationManager authmgr = m_engine.getAuthorizationManager();
			if (m_engine.pageExists(name)) {
				Permission permission = PermissionFactory.getPagePermission(m_engine.getPage(name), "modify");
				if (!authmgr.checkPermission(context.getWikiSession(), permission)) {
					log.debug("User does not have permission for this");
					return "Wiki.jsp?page=" + URLEncoder.encode(wikipage, "UTF-8");
				} else {
					wikipage = addInfo(name, description, lat, lon, filename);
				}
			} else {
				WikiPage page = new WikiPage(m_engine, name);
				Permission permission = PermissionFactory.getPagePermission(page, "edit");
				if (!authmgr.checkPermission(context.getWikiSession(), permission)) {
					log.debug("User does not have permission for this");
					return "Wiki.jsp?page=" + URLEncoder.encode(wikipage, "UTF-8");
				} else {
					wikipage = editNewPage(page, parent, description, lat, lon, filename);
					if (m_engine.pageExists(parent)) {
						WikiPage parent_page = m_engine.getPage(parent);
						String content = m_engine.getPureText(parent_page);
						String pluginText = content.substring(content.indexOf("[{OSM"));
						pluginText = pluginText.substring(0, pluginText.indexOf("}]") + 3);
						PluginContent pluginContent = PluginContent.parsePluginLine(context, pluginText, 0);
						if (pluginContent.getParameter("pages") != null) {
							String pages = pluginContent.getParameter("pages");
							String[] tempStrArray = content.split(pages);
							PageManager manager = m_engine.getPageManager();
							tempStrArray[0] += pages + "/" + name + tempStrArray[1];
							manager.putPageText(parent_page, tempStrArray[0]);
						}
					}
				}
			}
			/////

			long fileSize = actualFile.getSize();
			InputStream in = actualFile.getInputStream();

			try {

				executeUpload(context, in, filename, nextPage, wikipage, changeNote, fileSize);
				///// added by Hiroaki Tateshita
				nextPage = "Wiki.jsp?page=" + URLEncoder.encode(wikipage, "UTF-8");
				/////

			} finally {
				IOUtils.closeQuietly(in);
			}

		} catch (

		ProviderException e) {
			msg = "Upload failed because the provider failed: " + e.getMessage();
			log.warn(msg + " (attachment: " + attName + ")", e);

			throw new IOException(msg);
		} catch (IOException e) {
			// Show the submit page again, but with a bit more
			// intimidating output.
			msg = "Upload failure: " + e.getMessage();
			log.warn(msg + " (attachment: " + attName + ")", e);

			throw e;
		} catch (FileUploadException e) {
			// Show the submit page again, but with a bit more
			// intimidating output.
			msg = "Upload failure: " + e.getMessage();
			log.warn(msg + " (attachment: " + attName + ")", e);

			throw new IOException(msg, e);
		} catch (PluginException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			m_engine.getProgressManager().stopProgress(progressId);
			// FIXME: In case of exceptions should absolutely
			// remove the uploaded file.
		}

		return nextPage;
	}

	private String addInfo(String name, String description, float lat, float lon, String filename)
			throws ProviderException {
		WikiPage page = m_engine.getPage(name);
		String content = m_engine.getPureText(page);
		String[] contentSeparatedArray = content.split("!!!Reference");
		PageManager manager = m_engine.getPageManager();
		contentSeparatedArray[0] += "\n" + description + "\n";
		contentSeparatedArray[0] += "*Pic\n[{Image src='" + filename + "' width='300' }]\n";

		content = contentSeparatedArray[0] + "!!!Reference\n";
		if (contentSeparatedArray.length > 1) {
			content += contentSeparatedArray[1];
		}

		manager.putPageText(page, content);
		return name;
	}

	/**
	 * This method was added by Hiroaki Tateshita
	 * 
	 * @param name
	 * @param parent
	 * @param description
	 * @param lat
	 * @param lon
	 * @param filename
	 * @return the name of wikipage if no exception happens
	 * @throws ProviderException
	 */
	private String editNewPage(WikiPage page, String parent, String description, float lat, float lon, String filename)
			throws ProviderException {

		String content = createContent(page.getName(), parent, description, lat, lon, filename);

		PageManager manager = m_engine.getPageManager();
		manager.putPageText(page, content);

		return page.getName();

	}

	private String createContent(String name, String parent, String description, float lat, float lon,
			String filename) {
		String result = "[" + parent + "]" + System.lineSeparator() + "!!!Abstract" + System.lineSeparator();
		result += description + System.lineSeparator();
		result += "!!!Topics\n" + System.lineSeparator();
		if (lat < 90.0 || lon < 180.0) {
			result += "*Place\n" + lat + ", " + lon + System.lineSeparator();
			result += "[{OSM lat='" + lat + "' lon='" + lon + "'}]" + System.lineSeparator();
		}
		result += "*Pic\n[{Image src='" + filename + "' width='300' }]" + System.lineSeparator();
		result += "!!!Reference";
		return result;
	}

	/**
	 * Validates the next page to be on the same server as this webapp. Fixes
	 * [JSPWIKI-46].
	 */
	private String validateNextPage(String nextPage, String errorPage) {
		if (nextPage.indexOf("://") != -1) {
			// It's an absolute link, so unless it starts with our address,
			// we'll
			// log an error.

			if (!nextPage.startsWith(m_engine.getBaseURL())) {
				log.warn("Detected phishing attempt by redirecting to an unsecure location: " + nextPage);
				nextPage = errorPage;
			}
		}

		return nextPage;
	}

	/**
	 * Provides tracking for upload progress.
	 * 
	 */
	private static class UploadListener extends ProgressItem implements ProgressListener {
		public long m_currentBytes;
		public long m_totalBytes;

		public void update(long recvdBytes, long totalBytes, int item) {
			m_currentBytes = recvdBytes;
			m_totalBytes = totalBytes;
		}

		public int getProgress() {
			return (int) (((float) m_currentBytes / m_totalBytes) * 100 + 0.5);
		}
	}
}
