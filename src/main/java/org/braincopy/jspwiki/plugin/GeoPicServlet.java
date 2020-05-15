/*

Copyright (c) 2017-2020 Hiroaki Tateshita

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

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.security.Permission;
import java.security.Principal;
import java.util.List;
import java.util.Properties;
import javax.imageio.ImageIO;
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
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata.GPSInfo;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.wiki.pages.PageManager;
//import org.apache.wiki.PageManager;
import org.apache.wiki.WikiContext;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.api.exceptions.PluginException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.api.exceptions.RedirectException;
import org.apache.wiki.api.exceptions.WikiException;
import org.apache.wiki.attachment.Attachment;
import org.apache.wiki.attachment.AttachmentManager;
import org.apache.wiki.attachment.AttachmentServlet;
import org.apache.wiki.auth.AuthorizationManager;
import org.apache.wiki.auth.permissions.PermissionFactory;
import org.apache.wiki.i18n.InternationalizationManager;
import org.apache.wiki.parser.PluginContent;
import org.apache.wiki.preferences.Preferences;
import org.apache.wiki.ui.progress.ProgressItem;
import org.apache.wiki.util.TextUtil;

@WebServlet("/gpupload")
public class GeoPicServlet extends AttachmentServlet {

	/**
	 * List of attachment types which are allowed
	 */

	private String[] m_allowedPatterns;
	private String[] m_forbiddenPatterns;

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
	public void init(final ServletConfig config) throws ServletException {
		m_engine = WikiEngine.getInstance(config);
		super.init(config);

		final Properties props = m_engine.getWikiProperties();

		m_maxSize = TextUtil.getIntegerProperty(props, AttachmentManager.PROP_MAXSIZE, Integer.MAX_VALUE);

		final String allowed = TextUtil.getStringProperty(props, AttachmentManager.PROP_ALLOWEDEXTENSIONS, null);

		if (allowed != null && allowed.length() > 0)
			m_allowedPatterns = allowed.toLowerCase().split("\\s");
		else
			m_allowedPatterns = new String[0];
		final String forbidden = TextUtil.getStringProperty(props, AttachmentManager.PROP_FORBIDDENEXTENSIONS, null);

		if (forbidden != null && forbidden.length() > 0)
			m_forbiddenPatterns = forbidden.toLowerCase().split("\\s");
		else
			m_forbiddenPatterns = new String[0];
	}

	/**
	 * Uploads a specific mime multipart input set, intercepts exceptions.
	 *
	 * @param req The servlet request
	 * @return The page to which we should go next.
	 * @throws RedirectException   If there's an error and a redirection is needed
	 * @throws IOException         If upload fails
	 * @throws FileUploadException
	 */
	@Override
	protected String upload(final HttpServletRequest req) throws RedirectException, IOException {

		/////
		String msg = "";
		final String attName = "(unknown)";

		// If something bad happened, Upload should be able to take care of most
		// stuff
		final String errorPage = m_engine.getURL(WikiContext.ERROR, "", null, false);
		String nextPage = errorPage;

		final String progressId = req.getParameter("progressid");

		// Check that we have a file upload request
		if (!ServletFileUpload.isMultipartContent(req)) {
			throw new RedirectException("Not a file upload", errorPage);
		}

		try {
			final FileItemFactory factory = new DiskFileItemFactory();

			// Create the context _before_ Multipart operations, otherwise
			// strict servlet containers may fail when setting encoding.
			final WikiContext context = m_engine.createContext(req, WikiContext.ATTACH);

			final UploadListener pl = new UploadListener();

			m_engine.getProgressManager().startProgress(pl, progressId);

			final ServletFileUpload upload = new ServletFileUpload(factory);
			upload.setHeaderEncoding("UTF-8");
			if (!context.hasAdminPermissions()) {
				upload.setFileSizeMax(m_maxSize);
			}
			upload.setProgressListener(pl);

			// file size is checked in the parseRequest
			final List<FileItem> items = upload.parseRequest(req);

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
			boolean useExif = false;
			int properFileSize = 0;
			/////

			for (final FileItem item : items) {
				if (item.isFormField()) {
					if (item.getFieldName().equals("page")) {
						//
						// FIXME: Kludge alert. We must end up with the parent
						// page name,
						// if this is an upload of a new revision
						//

						wikipage = item.getString("UTF-8");
						final int x = wikipage.indexOf("/");

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

					} else if (item.getFieldName().equals("useExif")) {
						temp = item.getString("UTF-8");
						if (temp.equals("1")) {
							useExif = true;
						}

					} else if (item.getFieldName().equals("lat")) {

						temp = item.getString("UTF-8");
						if (!temp.equals("") && !useExif) {
							lat = Float.parseFloat(temp);
						}
					} else if (item.getFieldName().equals("lon")) {
						temp = item.getString("UTF-8");
						if (!temp.equals("") && !useExif) {
							lon = Float.parseFloat(temp);
						}
					} else if (item.getFieldName().equals("description")) {
						description = item.getString("UTF-8");
					}
					/////

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
			final String filename = actualFile.getName();

			final long fileSize = actualFile.getSize();
			InputStream in = actualFile.getInputStream();

			try {
				///// added by Hiroaki Tateshita

				final AuthorizationManager authmgr = m_engine.getAuthorizationManager();
				if (m_engine.pageExists(name)) {// if the page already exists, the infomation will be added.
					final Permission permission = PermissionFactory.getPagePermission(m_engine.getPage(name), "modify");
					if (!authmgr.checkPermission(context.getWikiSession(), permission)) {
						log.debug("User does not have permission for this");
						return "Wiki.jsp?page=" + URLEncoder.encode(wikipage, "UTF-8");
					} else {
						wikipage = addInfo(name, description, lat, lon, filename);
					}
				} else {//if the page does not exist, new page will be created.
					final WikiPage page = new WikiPage(m_engine, name);
					final Permission permission = PermissionFactory.getPagePermission(page, "edit");
					if (!authmgr.checkPermission(context.getWikiSession(), permission)) {
						log.debug("User does not have permission for this");
						m_engine.deletePage(name);
						return "Wiki.jsp?page=" + URLEncoder.encode(wikipage, "UTF-8");
					} else {
						wikipage = editNewPage(page, parent, description, lat, lon, filename);
						log.debug("#21 trying add created page: " + page + " to the parent page: " + parent
						+ " exist? " + m_engine.pageExists(parent) + ", contain? "
						+ m_engine.getPureText(page));
						
					}
				}
				/////

				///// added by Hiroaki Tateshita

				in = updateInfoAndAttachedFileByEXIF(in, filename, actualFile, useExif, properFileSize, fileSize, wikipage, parent, context);
				/////

				this.executeUpload(context, in, filename, nextPage, wikipage, changeNote, fileSize);

				///// added by Hiroaki Tateshita

				nextPage = "Wiki.jsp?page=" + URLEncoder.encode(wikipage, "UTF-8");
				/////

			} catch (final ImageReadException e) {
				// TODO Auto-generated catch block
				System.err.println("#10");
				e.printStackTrace();
			} catch (final ImageWriteException e) {
				// TODO Auto-generated catch block
				System.err.println("#11");
				e.printStackTrace();
			} finally {
				IOUtils.closeQuietly(in);
			}

		} catch (final ProviderException e) {
			msg = "Upload failed because the provider failed: " + e.getMessage();
			log.warn(msg + " (attachment: " + attName + ")", e);

			throw new IOException(msg);
		} catch (final IOException e) {
			// Show the submit page again, but with a bit more
			// intimidating output.
			msg = "Upload failure: " + e.getMessage();
			log.warn(msg + " (attachment: " + attName + ")", e);

			throw e;
		} catch (final FileUploadException e) {
			// Show the submit page again, but with a bit more
			// intimidating output.
			msg = "Upload failure: " + e.getMessage();
			log.warn(msg + " (attachment: " + attName + ")", e);

			throw new IOException(msg, e);
		} catch (final PluginException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			m_engine.getProgressManager().stopProgress(progressId);
			// FIXME: In case of exceptions should absolutely
			// remove the uploaded file.
		}

		return nextPage;
	}

	/**
	 * added by Hiroaki Tateshita
	 * 
	 * @param in
	 * @param filename
	 * @param actualFile
	 * @param useResize
	 * @param useExif
	 * @param properFileSize
	 * @param fileSize
	 * @param wikiPageName
	 * @return inputStream of updated file.
	 * @throws ImageReadException
	 * @throws ImageWriteException
	 * @throws IOException
	 * @throws ProviderException
	 * @throws PluginException
	 */
	private InputStream updateInfoAndAttachedFileByEXIF(InputStream in, String filename, FileItem actualFile,
			boolean useExif, int properFileSize, long fileSize, String wikiPageName, String parent, WikiContext context)
			throws ImageReadException, ImageWriteException, IOException, ProviderException, PluginException {
		InputStream result = null;
		final ImageMetadata metadata = Imaging.getMetadata(in, filename);

		if (metadata instanceof JpegImageMetadata) {
			// for getting rotation infomation
			// boolean isRotate = isRotateByExif(metadata, filename);

			// for getting lat and lon
			final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
			final TiffImageMetadata exif = jpegMetadata.getExif();
			if (exif != null) {
				final GPSInfo gpsinfo = exif.getGPS();

				if (gpsinfo != null && useExif) {
					final float lat = (float) gpsinfo.getLatitudeAsDegreesNorth();
					final float lon = (float) gpsinfo.getLongitudeAsDegreesEast();
					addLocationInfo(lat, lon, wikiPageName, parent, context);
				}

				final Short orientationValue = (Short) exif.getFieldValue(TiffTagConstants.TIFF_TAG_ORIENTATION);

				// for resizing
				// if (fileSize > properFileSize) {
				// log.info("#12 resize! filesize: " + fileSize + ", properFileSize: " +
				// properFileSize);
				// result = resizeFile(actualFile.getInputStream(), (float) properFileSize /
				// fileSize, isRotate);}
				result = resizeAndRotateFile(actualFile.getInputStream(), 1.0f, orientationValue);
			}
		} else {// for non-jpeg file
			result = actualFile.getInputStream();
		}

		return result;
	}

	/**
	 * created by Hiroaki Tateshita
	 * 
	 * @param in    inputStream of the image file
	 * @param scale 1.0 means same size
	 * @return inputStream of the scaled image file.
	 * @throws ImageReadException
	 * @throws IOException
	 * @throws ImageWriteException
	 */
	private InputStream resizeAndRotateFile(final InputStream in, final float scale, int orientationValue)
			throws ImageReadException, IOException, ImageWriteException {
		final String tmpDir = m_engine.getWorkDir();
		// final BufferedImage tempBufferedImage = Imaging.getBufferedImage(in);
		final BufferedImage tempBufferedImage = ImageIO.read(in);
		// for debug
		// final Graphics2D graphics = tempBufferedImage.createGraphics();

		// float scale = 1.0f;
		final int width = (int) ((float) tempBufferedImage.getWidth() * Math.sqrt(scale));
		final int height = width * tempBufferedImage.getHeight() / tempBufferedImage.getWidth();
		final Image tempScaledImage = tempBufferedImage.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);

		// Image object to BufferedImage object.

		final java.io.File tempImageFile = new java.io.File(
				tmpDir + File.separator + tempScaledImage.hashCode() + ".jpeg");
		log.debug("#13 new file! " + tempImageFile.getName() + " is scaled w :" + scale + " and orientation value is "
				+ orientationValue);

		BufferedImage buffered = null;
		Graphics2D graphics = null;

		// rotate?
		switch (orientationValue) {
			case 3:
				buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				graphics = buffered.createGraphics();
				graphics.rotate(Math.PI, width / 2, height / 2);
				break;
			case 6:
				buffered = new BufferedImage(height, width, BufferedImage.TYPE_INT_RGB);
				graphics = buffered.createGraphics();
				graphics.rotate(Math.PI / 2, height / 2, height / 2);
				break;
			default:
				buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				graphics = buffered.createGraphics();
				break;
		}

		graphics.drawImage(tempScaledImage, 0, 0, null);
		ImageIO.write(buffered, "jpeg", tempImageFile);

		// ImageIO.write(tempBufferedImage, "jpeg", tempImageFile);
		// Imaging.writeImage(buffered, tempImageFile, ImageFormats.PNG, null);
		// System.out.println("#14 tempImageFile: " + tempImageFile.getAbsolutePath());

		// when tomcat is turned off, tempImageFile will be deleted.
		tempImageFile.deleteOnExit();
		// System.out.println("#15 tempImageFile deleteOnExit! still exist?: " +
		// tempImageFile.getAbsolutePath());
		graphics.dispose();
		return new FileInputStream(tempImageFile);
	}

	/**
	 * overrided by Hiroaki Tateshita
	 * 
	 * @param context       the wiki context
	 * @param data          the input stream data
	 * @param filename      the name of the file to upload
	 * @param errorPage     the place to which you want to get a redirection
	 * @param parentPage    the page to which the file should be attached
	 * @param changenote    The change note
	 * @param contentLength The content length
	 * @return <code>true</code> if upload results in the creation of a new page;
	 *         <code>false</code> otherwise
	 * @throws RedirectException If the content needs to be redirected
	 * @throws IOException       If there is a problem in the upload.
	 * @throws ProviderException If there is a problem in the backend.
	 */
	protected boolean executeUpload(final WikiContext context, final InputStream data, String filename,
			final String errorPage, final String parentPage, final String changenote, final long contentLength)
			throws RedirectException, IOException, ProviderException {
		boolean created = false;
		log.debug("#7 Hello! contentLength: " + contentLength + ", m_maxSize: " + m_maxSize);

		try {
			filename = validateFileName(filename);
		} catch (final WikiException e) {
			// this is a kludge, the exception that is caught here contains the i18n key
			// here we have the context available, so we can internationalize it properly :
			throw new RedirectException(
					Preferences.getBundle(context, InternationalizationManager.CORE_BUNDLE).getString(e.getMessage()),
					errorPage);
		}

		//
		// FIXME: This has the unfortunate side effect that it will receive the
		// contents. But we can't figure out the page to redirect to
		// before we receive the file, due to the stupid constructor of
		// MultipartRequest.
		//

		log.debug("#8 Hello! contentLength: " + contentLength + ", m_maxSize: " + m_maxSize);
		if (!context.hasAdminPermissions()) {
			if (contentLength > m_maxSize) {
				// FIXME: Does not delete the received files.
				throw new RedirectException("File exceeds maximum size (" + m_maxSize + " bytes)", errorPage);
			}

			if (!isTypeAllowed(filename)) {
				throw new RedirectException("Files of this type may not be uploaded to this wiki", errorPage);
			}
		}

		final Principal user = context.getCurrentUser();

		final AttachmentManager mgr = m_engine.getAttachmentManager();

		log.debug("file=" + filename);

		if (data == null) {
			log.error("File could not be opened.");

			throw new RedirectException("File could not be opened.", errorPage);
		}

		//
		// Check whether we already have this kind of a page.
		// If the "page" parameter already defines an attachment
		// name for an update, then we just use that file.
		// Otherwise we create a new attachment, and use the
		// filename given. Incidentally, this will also mean
		// that if the user uploads a file with the exact
		// same name than some other previous attachment,
		// then that attachment gains a new version.
		//

		Attachment att = mgr.getAttachmentInfo(context.getPage().getName());

		if (att == null) {
			att = new Attachment(m_engine, parentPage, filename);
			created = true;
		}
		att.setSize(contentLength);

		//
		// Check if we're allowed to do this?
		//

		final Permission permission = PermissionFactory.getPagePermission(att, "upload");
		if (m_engine.getAuthorizationManager().checkPermission(context.getWikiSession(), permission)) {
			if (user != null) {
				att.setAuthor(user.getName());
			}

			if (changenote != null && changenote.length() > 0) {
				att.setAttribute(WikiPage.CHANGENOTE, changenote);
			}

			try {
				m_engine.getAttachmentManager().storeAttachment(att, data);
			} catch (final ProviderException pe) {
				// this is a kludge, the exception that is caught here contains the i18n key
				// here we have the context available, so we can internationalize it properly :
				throw new ProviderException(Preferences.getBundle(context, InternationalizationManager.CORE_BUNDLE)
						.getString(pe.getMessage()));
			}

			log.info("User " + user + " uploaded attachment to " + parentPage + " called " + filename + ", size "
					+ att.getSize());
		} else {
			throw new RedirectException("No permission to upload a file", errorPage);
		}

		return created;
	}

	private boolean isTypeAllowed(String name) {
		if (name == null || name.length() == 0)
			return false;

		name = name.toLowerCase();

		for (int i = 0; i < m_forbiddenPatterns.length; i++) {
			if (name.endsWith(m_forbiddenPatterns[i]) && m_forbiddenPatterns[i].length() > 0)
				return false;
		}

		for (int i = 0; i < m_allowedPatterns.length; i++) {
			if (name.endsWith(m_allowedPatterns[i]) && m_allowedPatterns[i].length() > 0)
				return true;
		}

		return m_allowedPatterns.length == 0;
	}

	/**
	 * Added by Hiroaki Tateshita
	 * 
	 * @param is
	 * @param filename
	 * @param name
	 * @throws ImageReadException
	 * @throws IOException
	 * @throws ProviderException
	 * @throws PluginException
	 */
	/*
	 * private void updateLocationByExif(final InputStream is, final String
	 * filename, final String name) throws ImageReadException, IOException,
	 * ProviderException {
	 * 
	 * final ImageMetadata metadata = Imaging.getMetadata(is, filename);
	 * System.out.println("#5hello!"); if (metadata instanceof JpegImageMetadata) {
	 * final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata; final
	 * TiffImageMetadata exif = jpegMetadata.getExif(); if (exif != null) { final
	 * GPSInfo gpsinfo = exif.getGPS();
	 * 
	 * if (gpsinfo != null) { final float lat = (float)
	 * gpsinfo.getLatitudeAsDegreesNorth(); final float lon = (float)
	 * gpsinfo.getLongitudeAsDegreesEast(); addLocationInfo(lat, lon, name); } } } }
	 */
	private void addLocationInfo(final float lat, final float lon, final String name, final String parent,
			WikiContext context) throws ProviderException, PluginException {
		final WikiPage page = m_engine.getPage(name);
		String content = m_engine.getPureText(page);
		if (!content.contains("[{OSM")) {
			final String[] tempStrArray = content.split("\\*Pic");
			content = tempStrArray[0] + "\n*Place\n" + lat + ", " + lon + System.lineSeparator();
			content += "[{OSM lat='" + lat + "' lon='" + lon + "'}]\n" + System.lineSeparator();
			content += "*Pic" + tempStrArray[1];
			final PageManager manager = m_engine.getPageManager();
			manager.putPageText(page, content);

		}

		if (m_engine.pageExists(parent) && m_engine.getPureText(page).contains("[{OSM")) {
			log.info("#23 trying add created page: " + page + " to the parent page: " + parent
					+ " exist? " + m_engine.pageExists(parent) + ", contain? "
					+ m_engine.getPureText(page).contains("[{OSM"));
			final WikiPage parent_page = m_engine.getPage(parent);
			content = m_engine.getPureText(parent_page);
			String pluginText = content.substring(content.indexOf("[{OSM"));
			pluginText = pluginText.substring(0, pluginText.indexOf("}]") + 3);
			final PluginContent pluginContent = PluginContent.parsePluginLine(context, pluginText, 0);
			if (pluginContent.getParameter("pages") != null) {
				final String pages = pluginContent.getParameter("pages");
				final String[] tempStrArray = content.split(pages);
				final PageManager manager = m_engine.getPageManager();
				tempStrArray[0] += pages + "/" + name + tempStrArray[1];
				manager.putPageText(parent_page, tempStrArray[0]);
			}
		}
	}

	private String addInfo(final String name, final String description, final float lat, final float lon,
			final String filename) throws ProviderException {
		final WikiPage page = m_engine.getPage(name);
		String content = m_engine.getPureText(page);
		final String[] contentSeparatedArray = content.split("!!!Reference");
		final PageManager manager = m_engine.getPageManager();
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
	private String editNewPage(final WikiPage page, final String parent, final String description, final float lat,
			final float lon, final String filename) throws ProviderException {

		final String content = createContent(page.getName(), parent, description, lat, lon, filename);

		final PageManager manager = m_engine.getPageManager();
		manager.putPageText(page, content);

		return page.getName();

	}

	private String createContent(final String name, final String parent, final String description, final float lat,
			final float lon, final String filename) {
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
	private String validateNextPage(String nextPage, final String errorPage) {
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

		public void update(final long recvdBytes, final long totalBytes, final int item) {
			m_currentBytes = recvdBytes;
			m_totalBytes = totalBytes;
		}

		public int getProgress() {
			return (int) (((float) m_currentBytes / m_totalBytes) * 100 + 0.5);
		}
	}

	/**
	 * 
	 * This method is copied from AttachmentManager.
	 * 
	 * Validates the filename and makes sure it is legal. It trims and splits and
	 * replaces bad characters.
	 *
	 * @param filename
	 * @return A validated name with annoying characters replaced.
	 * @throws WikiException If the filename is not legal (e.g. empty)
	 */
	static String validateFileName(String filename) throws WikiException {
		if (filename == null || filename.trim().length() == 0) {
			log.error("Empty file name given.");

			// the caller should catch the exception and use the exception text as an i18n
			// key
			throw new WikiException("attach.empty.file");
		}

		//
		// Should help with IE 5.22 on OSX
		//
		filename = filename.trim();

		// If file name ends with .jsp or .jspf, the user is being naughty!
		if (filename.toLowerCase().endsWith(".jsp") || filename.toLowerCase().endsWith(".jspf")) {
			log.info("Attempt to upload a file with a .jsp/.jspf extension.  In certain cases this "
					+ "can trigger unwanted security side effects, so we're preventing it.");
			//
			// the caller should catch the exception and use the exception text as an i18n
			// key
			throw new WikiException("attach.unwanted.file");
		}

		//
		// Some browser send the full path info with the filename, so we need
		// to remove it here by simply splitting along slashes and then taking the path.
		//

		final String[] splitpath = filename.split("[/\\\\]");
		filename = splitpath[splitpath.length - 1];

		//
		// Remove any characters that might be a problem. Most
		// importantly - characters that might stop processing
		// of the URL.
		//
		filename = StringUtils.replaceChars(filename, "#?\"'", "____");

		return filename;
	}

}
