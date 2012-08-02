package tk.djcrazy.libCC98;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownServiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import tk.djcrazy.libCC98.exception.NoUserFoundException;
import tk.djcrazy.libCC98.exception.ParseContentException;
import tk.djcrazy.libCC98.util.RegexUtil;
import android.accounts.NetworkErrorException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * 
 * This class take care of the connection with CC98, keep the cookies and the
 * user information.
 */
@Singleton
public class CC98ClientImpl implements ICC98Client {

	public static final String TAG = "CC98ClientImpl";
	@Inject
	private ICC98UrlManager manager;

	public final int PM_SEND_FAIL = -1;
	public final int PM_SEND_SUCC = 0;


	private Bitmap userImg;
 	private String username;
	private String passwd;
	
	public final String ID_PASSWD_ERROR_MSG = "用户名/密码错误";
	public final String SERVER_ERROR = "CC98服务器异常！";

 
	/**
	 * Don't using this, to avoid null pointer, use getHttpClient instead
	 */
	private DefaultHttpClient client = getHttpClient();

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#getLoginUserImg()
	 */
	@Override
	public Bitmap getLoginUserImg() {
		return userImg;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#setLoginUserImg(android.graphics.Bitmap)
	 */
	@Override
	public void setLoginUserImg(Bitmap bitmap) {
		userImg = bitmap;
	}

	@Override
	public void clearLoginInfo() {
		client.getCookieStore().clear();
		client.getCredentialsProvider().clear();
	}
	private DefaultHttpClient getHttpClient() {
		if (client == null) {
			HttpParams params = new BasicHttpParams();
			HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
			HttpProtocolParams.setContentCharset(params,
					HTTP.DEFAULT_CONTENT_CHARSET);
			HttpProtocolParams.setUseExpectContinue(params, true);
			SchemeRegistry schReg = new SchemeRegistry();
			schReg.register(new Scheme("http", PlainSocketFactory
					.getSocketFactory(), 80));
			schReg.register(new Scheme("https", SSLSocketFactory
					.getSocketFactory(), 443));
			ClientConnectionManager conMgr = new ThreadSafeClientConnManager(
					params, schReg);
			client = new DefaultHttpClient(conMgr, params);
			client.getParams().setParameter(
					CoreConnectionPNames.CONNECTION_TIMEOUT, 1500);
			client.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT,
					10000);
		}
		return client;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#doLogin(java.lang.String, java.lang.String)
	 */
	@Override
	public void doLogin(String id, String pw) throws ClientProtocolException,
			IOException, IllegalAccessException, ParseException, ParseContentException, NetworkErrorException {

		HttpResponse response;
		manager.setLifetoyVersion(false);
		HttpPost httpost = new HttpPost(manager.getLoginUrl());
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("username", id));
		nvps.add(new BasicNameValuePair("password", pw));
		nvps.add(new BasicNameValuePair("CookieDate", "3"));
		nvps.add(new BasicNameValuePair("loginaction", "login"));
		nvps.add(new BasicNameValuePair("userhidden", "2"));
		httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

		response = getHttpClient().execute(httpost);
		if (response.getStatusLine().getStatusCode() != 200) {
			throw new NetworkErrorException(SERVER_ERROR);
		}
		String sysMsg = EntityUtils.toString(response.getEntity());
		if (sysMsg.contains("密码错误") || sysMsg.contains("论坛错误信息")) {
			throw new IllegalAccessException(ID_PASSWD_ERROR_MSG);
		}
		username = id;
		passwd = pw;
// 		userImg = getUserImg(id);
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#setProxy(java.lang.String, int)
	 */
	@Override
	public void setProxy(String ip, int port) {
		HttpHost proxy = new HttpHost(ip, port);
		getHttpClient().getParams().setParameter(ConnRouteParams.DEFAULT_PROXY,
				proxy);
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#rmProxy()
	 */
	@Override
	public void rmProxy() {
		getHttpClient().getParams().removeParameter(
				ConnRouteParams.DEFAULT_PROXY);
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#pushNewPost(java.util.List, java.lang.String)
	 */
	@Override
	public boolean pushNewPost(List<NameValuePair> nvpsList, String boardID)
			throws ClientProtocolException, IOException {
		System.out.println("" + boardID);
		HttpEntity entity;
		HttpPost httpPost = new HttpPost(manager.getPushNewPostUrl(boardID));
		HttpResponse response = null;
		String html = null;
		httpPost.addHeader("Referer", manager.getPushNewPostReferer(boardID));
		nvpsList.add(new BasicNameValuePair("username", username));
		nvpsList.add(new BasicNameValuePair("passwd", passwd));
		// System.err.println("User Info:" + username + " " + passwd);
		httpPost.setEntity(new UrlEncodedFormEntity(nvpsList, HTTP.UTF_8));
		response = getHttpClient().execute(httpPost);
		entity = response.getEntity();
		if (entity != null) {
			html = EntityUtils.toString(entity);
			if (html.contains("状态：发表帖子成功")) {
				return true;
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#editPost(java.util.List, java.lang.String)
	 */
	@Override
	public boolean editPost(List<NameValuePair> nvpsList, String link)
			throws ClientProtocolException, IOException {
		HttpEntity entity;
		HttpPost httpPost = new HttpPost(link);
		httpPost.setEntity(new UrlEncodedFormEntity(nvpsList, HTTP.UTF_8));
		HttpResponse response = getHttpClient().execute(httpPost);
		entity = response.getEntity();
		if (entity != null) {
			String html = EntityUtils.toString(entity);
			// System.err.println("Reply Html:" + link + html.toLowerCase());

			if (html.contains("编辑帖子成功")) {
				return true;
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#submitReply(java.util.List, java.lang.String, java.lang.String)
	 */
	@Override
	public boolean submitReply(List<NameValuePair> nvpsList, String boardID,
			String rootID) throws ClientProtocolException, IOException {
		HttpEntity entity;
		HttpPost httpPost = new HttpPost(manager.getSubmitReplyUrl(boardID));
		HttpResponse response = null;
		String html = null;

		httpPost.addHeader("Referer",
				manager.getSubmitReplyReferer(boardID, rootID));
		nvpsList.add(new BasicNameValuePair("username", username));
		nvpsList.add(new BasicNameValuePair("passwd", passwd));
		httpPost.setEntity(new UrlEncodedFormEntity(nvpsList, HTTP.UTF_8));
		response = getHttpClient().execute(httpPost);
		entity = response.getEntity();

		if (entity != null) {
			html = EntityUtils.toString(entity);
			if (html.contains("状态：回复帖子成功")) {
				return true;
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#queryPosts(java.lang.String, java.lang.String, java.lang.String, int, int)
	 */
	@Override
	public String queryPosts(String keyWord, String sType, String searchDate,
			int boardArea, int boardID) throws ParseException, IOException {
		/*
		 * action: queryresult.asp name=keyword name=sType, value=2: 主题关键字搜索,
		 * value=1: 主题作者搜索 name=SearchDate, value=ALL: 所有日期, value=1: 昨天以来 ,
		 * value=5: 5天以来, value=10: 10天以来, value=30: 30天以来 name=boardarea,
		 * value=0: 全站搜索 name=boardid, name=serType, value=1: 所有帖子, value=2:
		 * 精华贴, value=3: 保存贴
		 */
		List<NameValuePair> nvpsList = new ArrayList<NameValuePair>();
		HttpEntity entity;
		HttpPost httpPost = new HttpPost(manager.getQueryUrl());
		HttpResponse response = null;
		String html = null;
		httpPost.addHeader("Referer", manager.getQueryReferer());
		nvpsList.add(new BasicNameValuePair("keyword", keyWord));
		nvpsList.add(new BasicNameValuePair("sType", sType));
		nvpsList.add(new BasicNameValuePair("SearchDate", searchDate));
		nvpsList.add(new BasicNameValuePair("boardarea", String
				.valueOf(boardArea)));
		nvpsList.add(new BasicNameValuePair("serType", String.valueOf(boardID)));

		httpPost.setEntity(new UrlEncodedFormEntity(nvpsList, HTTP.UTF_8));
		response = getHttpClient().execute(httpPost);
		entity = response.getEntity();

		if (entity != null) {
			html = EntityUtils.toString(entity);
			if (html.contains("搜索主题共查询到")) {
				return html;
			} else if (html.contains("没有找到您要查询的内容")) {
				System.err.println("没有找到您要查询的内容!");
				return "";
			}
		}

		return null;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#getPage(java.lang.String)
	 */
	@Override
	public String getPage(String link) throws ClientProtocolException,
			IOException, ParseException {
		HttpGet get = new HttpGet(link);
		Log.d(TAG, "get page: "+link);
		HttpResponse rsp = null;
		rsp = getHttpClient().execute(get);
		int mCode = rsp.getStatusLine().getStatusCode();
		if (mCode != 200) {
			throw new IOException("服务器有误！");
		}
		HttpEntity entity = rsp.getEntity();
		String content = EntityUtils.toString(entity);
		return content;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#uploadPictureToCC98(java.io.File)
	 */
	@Override
	public String uploadPictureToCC98(File picFile)
			throws PatternSyntaxException, MalformedURLException, IOException,
			ParseContentException {

		URL url = new URL(manager.getUploadPictureUrl());
		HttpURLConnection uc = (HttpURLConnection) url.openConnection();
		// 上传图片的一些参数设置
		String cookieString = "";
		for (Cookie cookie : getHttpClient().getCookieStore().getCookies()) {
			cookieString += cookie.getName() + "=" + cookie.getValue() + "; ";
		}
		uc.setRequestProperty("Cookie", cookieString);
		uc.setRequestProperty("User-Agent",
				"Apache-HttpClient/4.1.2 (java 1.5)");
		uc.setRequestProperty("Connection", "Keep-Alive");
		uc.setRequestProperty("Host", "www.cc98.org");
		uc.setRequestProperty("Content-Type",
				"multipart/form-data; boundary=iZhqsAXWbyxTJ01lIrUiXj_AGU2675rDPk");
		uc.addRequestProperty("Cookie2", "$Version=1");
		uc.setDoOutput(true);
		uc.setUseCaches(true);
		// 读取文件流
		int size = (int) picFile.length();
		byte[] data = new byte[size];
		FileInputStream fis = new FileInputStream(picFile);
		OutputStream out = uc.getOutputStream();
		fis.read(data, 0, size);
		String temp = "--iZhqsAXWbyxTJ01lIrUiXj_AGU2675rDPk\r\n";
		out.write(temp.getBytes());
		temp = "Content-Disposition: form-data; name=\"file\"; "
				+ "filename=\"" + picFile.getName() + "\"\r\n";
		out.write(temp.getBytes());
		temp = "Content-Type: application/octet-stream\r\nContent-Transfer-Encoding: binary\r\n\r\n";
		out.write(temp.getBytes());
		// 写入图片流
		out.write(data);
		temp = "\n--iZhqsAXWbyxTJ01lIrUiXj_AGU2675rDPk\r\nContent-Disposition: form-data; name=\"descript\"\r\nContent-Type: text/plain; charset=US-ASCII\r\nContent-Transfer-Encoding: 8bit\r\n";
		out.write(temp.getBytes());
		temp = "0431.la\r\n--iZhqsAXWbyxTJ01lIrUiXj_AGU2675rDPk--\r\n";
		out.write(temp.getBytes());
		out.flush();
		out.close();
		fis.close();
		// 读取响应数据
		int code = uc.getResponseCode();
		String sCurrentLine = "";
		// 存放响应结果
		String sTotalString = "";
		if (code == 200) {
			InputStream is = uc.getInputStream();
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(is));
			while ((sCurrentLine = reader.readLine()) != null)
				if (sCurrentLine.length() > 0)
					sTotalString = sTotalString + sCurrentLine.trim();
		} else {
			throw new IllegalStateException("upload error");
		}
		return RegexUtil.getMatchedString(
				CC98ParseRepository.UPLOAD_PIC_ADDRESS_REGEX, sTotalString)
				.replace(",1", "");
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#getInboxHtml(int)
	 */
	@Override
	public String getInboxHtml(int pageNumber) throws ClientProtocolException,
			ParseException, IOException {
		return getPage(manager.getInboxUrl(pageNumber));
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#getOutboxHtml(int)
	 */
	@Override
	public String getOutboxHtml(int pageNumber)
			throws ClientProtocolException, ParseException, IOException {
		return getPage(manager.getOutboxUrl(pageNumber));
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#getUserImgUrl(java.lang.String)
	 */
	@Override
	public String getUserImgUrl(String userName)
			throws ClientProtocolException, ParseException, IOException,
			ParseContentException {

		String html = getPage(manager.getUserProfileUrl(userName));
		String url = RegexUtil.getMatchedString(
				CC98ParseRepository.USER_PROFILE_AVATAR_REGEX, html);
		if (!url.startsWith("http") && !url.startsWith("ftp")) {
			url = manager.getClientUrl() + url;
		}
		return url;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#getPasswd()
	 */
	@Override
	public String getPasswd() {
		return passwd;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#getUserName()
	 */
	@Override
	public String getUserName() {
		return username;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#setPassword(java.lang.String)
	 */
	@Override
	public void setPassword(String pwd) {
		passwd = pwd;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#setUserName(java.lang.String)
	 */
	@Override
	public void setUserName(String name) {
		username = name;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#sendPm(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public int sendPm(String touser, String title, String message)
			throws ClientProtocolException, IOException {
		HttpPost post = new HttpPost(manager.getSendPmUrl());
		List<NameValuePair> msgInfo = new ArrayList<NameValuePair>();
		msgInfo.add(new BasicNameValuePair("touser", touser));
		msgInfo.add(new BasicNameValuePair("title", title));
		msgInfo.add(new BasicNameValuePair("message", message));
		HttpResponse response = null;
		int flag = PM_SEND_FAIL;

		post.setEntity(new UrlEncodedFormEntity(msgInfo, HTTP.UTF_8));
		response = getHttpClient().execute(post);

		HttpEntity entity = response.getEntity();

		if (entity != null
				&& EntityUtils.toString(response.getEntity()).contains("论坛成功")) {
			flag = PM_SEND_SUCC;
		}

		return flag;
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#addFriend(java.lang.String)
	 */
	@Override
	public void addFriend(String userId) throws ParseException,
			NoUserFoundException, IOException {
		if (userId == null) {
			throw new IllegalArgumentException("Null argument!");
		}
		HttpPost httpPost = new HttpPost(manager.getAddFriendUrl());
		httpPost.addHeader("Referer", manager.getAddFriendUrlReferrer());
		List<NameValuePair> nvpsList = new ArrayList<NameValuePair>();
		nvpsList.add(new BasicNameValuePair("todo", "saveF"));
		nvpsList.add(new BasicNameValuePair("touser", userId));
		nvpsList.add(new BasicNameValuePair("Submit", "保存"));
		httpPost.setEntity(new UrlEncodedFormEntity(nvpsList, HTTP.UTF_8));
		HttpResponse response = getHttpClient().execute(httpPost);
		if (response.getStatusLine().getStatusCode() != 200) {
			throw new ConnectException("服务器返回错误！");
		}
		HttpEntity entity = response.getEntity();
		if (entity != null) {
			String html = EntityUtils.toString(entity);
			if (html.contains("好友添加成功")) {
				return;
			} else if (html.contains("论坛没有这个用户，操作未成功")) {
				throw new NoUserFoundException("不存在此用户！");
			} else {
				throw new UnknownServiceException("未知错误！");
			}
		} else {
			throw new ConnectException("服务器返回错误！");
		}
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#getUserProfileHtml(java.lang.String)
	 */
	@Override
	public String getUserProfileHtml(String userName)
			throws NoUserFoundException, IOException {
		String mString = getPage(manager.getUserProfileUrl(userName));
		if (mString.contains("您查询的名字不存在")) {
			throw new NoUserFoundException("您查询的名字不存在");
		} else if (mString.contains("用户头衔")) {
			return mString;
		} else {
			throw new IOException("网络连接有误");
		}
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#getBitmapFromUrl(java.lang.String)
	 */

	@Override
	public Bitmap getBitmapFromUrl(String url) throws IOException {
		if (url == null) {
			throw new IllegalArgumentException();
		}
		Log.d("IMG", url);
		BufferedInputStream bis = null;
		URL img_url = new URL(url);
		URLConnection conn = img_url.openConnection();
		conn.setConnectTimeout(5000);
		conn.connect();
		InputStream in = conn.getInputStream();
		bis = new BufferedInputStream(in);
		Log.d("IMG", bis.toString());
		return BitmapFactory.decodeStream(bis);

	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#getUserImg(java.lang.String)
	 */
	@Override
	public Bitmap getUserImg(String userName) throws ClientProtocolException,
			ParseException, IOException, ParseContentException {
		return getBitmapFromUrl(getUserImgUrl(userName));
	}

	/* (non-Javadoc)
	 * @see tk.djcrazy.libCC98.ICC98Client#doHttpBasicAuthorization(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean doHttpBasicAuthorization(String authName, String authPassword)
			throws ClientProtocolException, IOException, URISyntaxException {
		URI uri = null;
		uri = new URI(manager.getClientUrl());
		getHttpClient().getCredentialsProvider().setCredentials(
				new AuthScope(uri.getHost(), uri.getPort(),
						AuthScope.ANY_SCHEME),
				new UsernamePasswordCredentials(authName, authPassword));

 		HttpGet request = new HttpGet(uri);

		HttpResponse response = getHttpClient().execute(request);
 		if (response.getStatusLine().getStatusCode() == 200) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public String getDomain() {
		return manager.getClientUrl();
	}
}
