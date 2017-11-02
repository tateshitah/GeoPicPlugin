package org.braincopy.jspwiki.plugin;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.wiki.WikiEngine;

@WebServlet("/gpupload")
public class GeoPicServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private WikiEngine m_engine;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			m_engine = WikiEngine.getInstance(config);
		} catch (org.apache.wiki.InternalWikiException e) {
			// once failed, try again just once. it's because of Servlet 3.0. JSPWiki's
			// engine seems to try to check web.xml but no need for servlet 3.0.
			m_engine = WikiEngine.getInstance(config);
		}
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

		response.setContentType("text/html; charset=UTF-8");
		PrintWriter out = response.getWriter();

		out.println(createHTML("GET"));

		out.close();
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

		response.setContentType("text/html; charset=UTF-8");
		PrintWriter out = response.getWriter();

		String[] tempStrs;
		tempStrs = request.getParameterValues("name");
		if(tempStrs!=null) {
			out.println(tempStrs[0]+"\n");
		}else {
			out.println("name is null\n");
		}
		tempStrs = request.getParameterValues("parent_page");
		if(tempStrs!=null) {
			out.println(tempStrs[0]+"\n");
		}else {
			out.println("parent_page is null\n");
		}
		tempStrs = request.getParameterValues("location");
		if(tempStrs!=null) {
			out.println(tempStrs[0]+"\n");
		}else {
			out.println("location is null\n");
		}
		
		if(ServletFileUpload.isMultipartContent(request)) {
			out.println("it should be file upload\n");
		}else {
			out.println("it might be not file upload\n");
		}
		
		out.println(createHTML("POST"));

		out.close();
	}

	protected String createHTML(String methodType) {
		StringBuffer sb = new StringBuffer();

		sb.append("<html>");
		sb.append("<head>");
		sb.append("<title>サンプル</title>");
		sb.append("</head>");
		sb.append("<body>");

		sb.append("<p>");
		sb.append(methodType);
		sb.append("メソッドで呼び出されました</p>");

		sb.append("<p><a href=\"http://localhost:9627/personal/helloworld\">リンク</a></p>");

		sb.append("<form action=\"http://localhost:9627/personal/helloworld\" method=\"get\">");
		sb.append("<input type=\"submit\" value=\"GETで送信\">");
		sb.append("</form>");

		sb.append("<form action=\"http://localhost:9627/personal/helloworld\" method=\"post\">");
		sb.append("<input type=\"submit\" value=\"POSTで送信\">");
		sb.append("</form>");
		sb.append(m_engine.getApplicationName());

		sb.append("</body>");
		sb.append("</html>");

		return (new String(sb));
	}

	/*
	 * @Override(non-Javadoc)
	 * 
	 * @see
	 * javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest,
	 * javax.servlet.http.HttpServletResponse)
	 */
	/*
	 * protected void doPost(HttpServletRequest req, HttpServletResponse resp)
	 * throws ServletException, IOException { super.doPost(req, resp);
	 * resp.setContentType("text/html"); PrintWriter out = resp.getWriter();
	 * out.println("hello " + m_engine.getApplicationName()); }
	 * 
	 * protected void doGet(HttpServletRequest request, HttpServletResponse
	 * response) throws ServletException, IOException {
	 * 
	 * response.setContentType("text/html"); PrintWriter out = response.getWriter();
	 * out.println(""); out.println(""); out.println(new java.util.Date());
	 * out.println(""); out.println(""); }
	 */
}
