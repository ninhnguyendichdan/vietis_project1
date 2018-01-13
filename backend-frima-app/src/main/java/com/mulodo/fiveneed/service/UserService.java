package com.mulodo.fiveneed.service;

import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import javax.sql.DataSource;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.mulodo.fiveneed.bean.RequestBean;
import com.mulodo.fiveneed.bean.ResponseBean;
import com.mulodo.fiveneed.bean.request.SocialLoginRequestBean;
import com.mulodo.fiveneed.bean.response.LoginResponseBean;
import com.mulodo.fiveneed.bean.response.SocialLoginResponse;
import com.mulodo.fiveneed.common.util.CommonUtil;
import com.mulodo.fiveneed.common.util.EmailUtil;
import com.mulodo.fiveneed.common.util.StringUtils;
import com.mulodo.fiveneed.constant.AppHttpStatus;
import com.mulodo.fiveneed.constant.Constants;
import com.mulodo.fiveneed.constant.EnvironmentKey;
import com.mulodo.fiveneed.entity.MstUser;
import com.mulodo.fiveneed.entity.MstUserProfile;
import com.mulodo.fiveneed.entity.TblChat;
import com.mulodo.fiveneed.entity.TblProduct;
import com.mulodo.fiveneed.entity.TblQuestion;
import com.mulodo.fiveneed.repository.ChatRepository;
import com.mulodo.fiveneed.repository.PaymentRepositoryJPA;
import com.mulodo.fiveneed.repository.ProductOrderRepository;
import com.mulodo.fiveneed.repository.ProductRepository;
import com.mulodo.fiveneed.repository.QuestionRepository;
import com.mulodo.fiveneed.repository.UserProfileRepository;
import com.mulodo.fiveneed.repository.UserRepository;

@Service("UserService")
@Transactional(rollbackFor = Exception.class)
public class UserService extends BaseService {

	@Autowired
	DataSource dataSource;

	@Autowired
	UserRepository userDao;

	@Autowired
	ProductOrderRepository productOrderRepo;

	@Autowired
	ProductRepository productRepo;

	@Autowired
	PaymentRepositoryJPA paymentRepo;

	@Autowired
	UserProfileRepository profileRepo;

	@Autowired
	QuestionRepository questionRepo;

	@Autowired
	ChatRepository chatRepo;

	public void getProfile(ResponseBean response) {
		MstUser user = checkTokenInSession();
		if (user == null) {
			response.setStatus(AppHttpStatus.AUTH_FAILED);
			return;
		}
		MstUserProfile profile = profileRepo.findOne(user.getId());
		response.setData(profile);
	}

	/*
	 * API M05
	 * 
	 * @author Danhloc
	 * 
	 * @param request
	 * 
	 * @return
	 */
	public void regiserSystem(MstUser user, ResponseBean response) {
		MstUser mstuser = userDao.findByEmailAndIsSysUserAndStatusAndProviderType(user.getEmail(), false,
				MstUser.ACTIVE, MstUser.PROVIDER_TYPE_LOCAL);
		if (mstuser != null) {
			response.setStatus(AppHttpStatus.ALREADY_EXISTS_USER_ID);
		}
		user.setSalt(CommonUtil.randomDecimalString(Constants.SALT_LENGTH));
		user.setPassword(CommonUtil.encryptPassword(user.getPassword(), user.getSalt(),
				Integer.parseInt(environment.getProperty(EnvironmentKey.SHA256_LOOPNUMBER_KEY.getKey()))));
		user.setUserName(user.getUserName());
		user.setStatus(MstUser.CONFIRM);
		user.setIsSysUser(false);
		user.setProviderType(MstUser.PROVIDER_TYPE_LOCAL);
		user.setCreatedAt(CommonUtil.getCurrentTime());
		user.setIsDeleted(false);
		user.setActivationKey(UUID.randomUUID().toString());
		userDao.save(user);
		MstUserProfile profile = new MstUserProfile();
		profile.setUserId(user.getId());
		profile.setCreatedBy(user.getId());
		profile.setUserName(user.getUserName());
		profile.setEmail(user.getEmail());
		profile.setStatus(user.getStatus());
		profile.setCreatedAt(user.getCreatedAt());
		profile.setIsDeleted(user.getIsDeleted());
		profileRepo.save(profile);

		// TODO send mail , chuoi active_key

	}

	/*
	 * API M06
	 * 
	 * @author Danhloc
	 * 
	 * @param request
	 * 
	 * @return
	 */
	public void activeSystem(String activation_key, ResponseBean response) {
		MstUser user = userDao.findOneByActivationKey(activation_key);
		if (user == null) {
			response.setStatus(AppHttpStatus.FAILED_TO_REGISTER_DATA);
			return;
		}
		user.setStatus(MstUser.ACTIVE);
		userDao.save(user);
	}

	/*
	 * API M01
	 * 
	 * @author Danhloc
	 * 
	 * @param request
	 * 
	 * @return
	 */
	public void loginSystem(MstUser userRequest, ResponseBean response) {
		LoginResponseBean bean = new LoginResponseBean();
		MstUser user = userDao.findByEmailAndIsDeleted(userRequest.getEmail(), false);
		if (user == null || user.getIsDeleted()) {
			response.setStatus(AppHttpStatus.AUTH_FAILED);
			return;
		} else if (user.getStatus().equalsIgnoreCase(MstUser.CONFIRM)) {
			response.setStatus(AppHttpStatus.EMAIL_NEED_TO_CONFIRM);
			return;
		}
		String password = CommonUtil.encryptPassword(userRequest.getPassword(), user.getSalt(),
				Integer.parseInt(environment.getProperty(EnvironmentKey.SHA256_LOOPNUMBER_KEY.getKey())));
		if (password.equals(user.getPassword())) {
			String token = UUID.randomUUID().toString();
			user.setAccessToken(token);
			bean.setAccessToken(token);
			userDao.save(user);
			MstUserProfile profile = profileRepo.findOne(user.getId());
			bean.setUserprofile(profile);
		} else {
			response.setStatus(AppHttpStatus.AUTH_FAILED);
			return;
		}
		response.setData(bean);
	}

	/*
	 * API M02
	 * 
	 * @author Danhloc
	 * 
	 * @param request
	 * 
	 * @return
	 */
	public void logOut(RequestBean request, ResponseBean response) {
		MstUser mstUser = checkTokenInSession();
		if (mstUser == null) {
			response.setStatus(AppHttpStatus.AUTH_FAILED);
			return;
		} else {
			mstUser.setAccessToken(null);
			response.setStatus(AppHttpStatus.SUCCESS);
			userDao.save(mstUser);
			return;
		}
	}

	public void updateProfile(MstUserProfile profile, ResponseBean response) {
		MstUser user = checkTokenInSession();
		if (user == null || !profile.getId().equals(user.getId())) {
			response.setStatus(AppHttpStatus.AUTH_FAILED);
			return;
		}
		MstUserProfile profileUpdate = profileRepo.findOne(user.getId());
		profileUpdate.setUpdatedAt(CommonUtil.getCurrentTime());
		profileUpdate.setUpdatedBy(user.getId());
		profileUpdate.copyInfo(profile);
		profileRepo.save(profileUpdate);

		user.setUpdatedAt(CommonUtil.getCurrentTime());
		user.setUpdatedBy(user.getId());
		if (!CommonUtil.isEmpty(profile.getEmail()))
			user.setEmail(profile.getEmail());
		if (!CommonUtil.isEmpty(profile.getUserName()))
			user.setUserName(profile.getUserName());
		userDao.save(user);
		response.setData(profileUpdate);
	}

	public void updateProfileSetting(MstUser profile, ResponseBean response) {
		MstUser user = checkTokenInSession();
		if (user == null || !profile.getId().equals(user.getId())) {
			response.setStatus(AppHttpStatus.AUTH_FAILED);
			return;
		}

		String oldPassEncrypt = CommonUtil.encryptPassword(profile.getOldPassword(), user.getSalt(),
				Integer.parseInt(environment.getProperty(EnvironmentKey.SHA256_LOOPNUMBER_KEY.getKey())));

		if (!user.getPassword().equalsIgnoreCase(oldPassEncrypt) && !CommonUtil.isEmpty(profile.getPassword())) {
			// Nếu có nhập mật khẩu mới và mật khẩu cũ không đúng
			response.setStatus(AppHttpStatus.AUTH_FAILED);
			return;
		} else if (!CommonUtil.isEmpty(profile.getPassword())) {
			user.setPassword(CommonUtil.encryptPassword(profile.getPassword(), user.getSalt(),
					Integer.parseInt(environment.getProperty(EnvironmentKey.SHA256_LOOPNUMBER_KEY.getKey()))));
		}

		if (!profile.getEmail().equalsIgnoreCase(user.getEmail())) {
			user.setStatus(MstUser.CONFIRM);
			user.setActivationKey(UUID.randomUUID().toString());
			// Cho tài khoản đó hết session luôn
			user.setAccessToken("");
			// TODO: Gửi mail verify email
		}

		user.setUpdatedAt(CommonUtil.getCurrentTime());
		user.setUpdatedBy(user.getId());

		userDao.save(user);
		response.setData(user);
	}

	public void leaveMember(ResponseBean response) {
		MstUser user = checkTokenInSession();
		if (user == null) {
			response.setStatus(AppHttpStatus.AUTH_FAILED);
			return;
		}

		List<TblProduct> productList = productRepo.findByStatusAndCreatedBy(TblProduct.STATUS_BUYING, user.getId());

		// User is buying: leave denied
		if (!productList.isEmpty()) {
			response.setStatus(AppHttpStatus.USER_BUYING);
			return;
		} else {
			MstUserProfile profile = profileRepo.findOne(user.getId());
			user.setStatus(MstUser.LEAVE);
			profile.setStatus(MstUser.LEAVE);
			user.setIsDeleted(true);
			profile.setIsDeleted(true);

			user.setUpdatedAt(CommonUtil.getCurrentTime());
			user.setUpdatedBy(user.getId());

			profile.setUpdatedAt(CommonUtil.getCurrentTime());
			profile.setUpdatedBy(user.getId());

			profileRepo.save(profile);
			userDao.save(user);
			List<TblProduct> publicList = productRepo.findByStatusAndCreatedBy(TblProduct.STATUS_PUBLISHED,
					user.getId());
			for (TblProduct t : publicList) {
				t.setStatus(TblProduct.STATUS_DRAFT);
			}
			// Update product to draft
			if (!publicList.isEmpty())
				productRepo.save(publicList);
			response.setStatus(AppHttpStatus.SUCCESS);
		}
	}

	public void saveContact(TblQuestion question, ResponseBean response) {
		MstUser user = checkTokenInSession();
		if (user == null) {
			response.setStatus(AppHttpStatus.AUTH_FAILED);
			return;
		}
		question.setCreatedAt(CommonUtil.getCurrentTime());
		question.setCreatedBy(user.getId());
		questionRepo.save(question);
	}

	public void getComment(int page, int size, String sortBy, String sortType, ResponseBean response) {
		MstUser user = checkTokenInSession();
		if (user == null) {
			response.setStatus(AppHttpStatus.AUTH_FAILED);
			return;
		}
		String sortByProperty = StringUtils.snakeCaseToCamelCase(sortBy);
		Sort.Order order = new Sort.Order(
				Constants.ORDER_ASC.equalsIgnoreCase(sortType) ? Direction.ASC : Direction.DESC, sortByProperty)
						.ignoreCase();
		Page<TblChat> resultPage = chatRepo.searchComment(user.getId(), new PageRequest(page, size, new Sort(order)));
		response.setData(resultPage);
	}

	/*
	 * API M03
	 * 
	 * @author Danhloc
	 * 
	 * @param request
	 * 
	 * @return
	 */
	public void resetPassword(MstUser user, ResponseBean response) {
		user = userDao.findByEmailAndStatusAndIsDeleted(user.getEmail(), MstUser.ACTIVE, false);
		if (user == null) {
			response.setStatus(AppHttpStatus.AUTH_FAILED);
			return;
		}
		// ramdom character string number
		String password = CommonUtil.randomDecimalString(Constants.CHARACTER_STRING_RANDOM);
		user.setSalt(CommonUtil.randomDecimalString(Constants.SALT_LENGTH));
		user.setPassword(CommonUtil.encryptPassword(password, user.getSalt(),
				Integer.parseInt(environment.getProperty(EnvironmentKey.SHA256_LOOPNUMBER_KEY.getKey()))));
		System.out.println(password);
		userDao.save(user);
		// TODO: waiting for confirm
		VelocityContext context = initVelocity();
		context.put("loginid", user.getEmail());
		context.put("temppass", password);
		Template template = Velocity.getTemplate("template/email_template_temp_pass.html");
		StringWriter sw = new StringWriter();
		template.merge(context, sw);

//		sendEmail(environment.getProperty("mail.user"), user.getEmail(), "Here is your new password", sw.toString());

	}

	private void sendEmail(String fromAddr, String toAddr, String subject, String contentSend) {
		// get app param for email

		String username = environment.getProperty("mail.amzs3.access_key_id");
		String password = environment.getProperty("mail.amzs3.secret_access_key");

		String host = environment.getProperty("mail.host");
		String port = environment.getProperty("mail.port");

		Properties props = System.getProperties();
		props.put("mail.transport.protocol", "smtp");
		props.put("mail.smtp.port", port);
		props.put("mail.smtp.ssl.enable", "true");
		props.put("mail.smtp.auth", "true");

		Session session = Session.getDefaultInstance(props);
		Transport transport = null;

		try {

			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(fromAddr));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddr));
			message.setSubject(MimeUtility.encodeText(subject, "utf-8", "B"));
			message.setContent(contentSend, "text/html;charset=UTF-8");

			transport = session.getTransport();

			// Send the message.
			System.out.println("Sending...");

			// Connect to Amazon SES using the SMTP username and password
			// you specified above.
			transport.connect(host, username, EmailUtil.makeSMTPPassword(password));
			transport.sendMessage(message, message.getAllRecipients());
			System.out.println("Sent email Done");
		} catch (Exception e) {
			logger.error("SendEmail Error:", e);
		} finally {
			try {
				transport.close();
			} catch (MessagingException e) {
				logger.error("SendEmail Error:", e);
			}
		}
	}

	private VelocityContext initVelocity() {
		Properties p = new Properties();
		p.setProperty("resource.loader", "class");
		p.setProperty("class.resource.loader.class",
				"org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
		Velocity.init(p);
		VelocityContext context = new VelocityContext();
		return context;
	}

	private SocialLoginResponse getSocialInfo(SocialLoginRequestBean bean) {
		SocialLoginResponse fuser = null;
		if (MstUser.PROVIDER_TYPE_FACEBOOK.equalsIgnoreCase(bean.getProviderType())) {
			try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
				HttpGet request = new HttpGet(
						"https://graph.facebook.com/me?fields=id,name,email&access_token=" + bean.getProviderToken());
				request.addHeader("content-type", "application/json");
				HttpResponse result = httpClient.execute(request);
				String json = EntityUtils.toString(result.getEntity(), "UTF-8");
				ObjectMapper mapper = new ObjectMapper();

				fuser = mapper.readValue(json, SocialLoginResponse.class);
			} catch (Exception e) {
				logger.error("getSocialInfo", e);
			}
		} else {
			String GOOGLE_CLIENT_ID = "885156838152-8480mrm9267v1ve2a5pjhbcilc0gk401.apps.googleusercontent.com";
			JacksonFactory jacksonFactory = new JacksonFactory();

			try {

				GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
						jacksonFactory).setAudience(Collections.singletonList(GOOGLE_CLIENT_ID))
								// Or, if multiple clients access the backend:
								// .setAudience(Arrays.asList(CLIENT_ID_1,
								// CLIENT_ID_2, CLIENT_ID_3))
								.build();

				// (Receive idTokenString by HTTPS POST)

				GoogleIdToken idToken = verifier.verify(bean.getProviderToken());
				if (idToken != null) {
					GoogleIdToken.Payload payload = idToken.getPayload();
					if (!GOOGLE_CLIENT_ID.equals(payload.getAudience())) {
						throw new IllegalArgumentException("Audience mismatch");
					}

					fuser = new SocialLoginResponse();
					fuser.setEmail(payload.getEmail());
					fuser.setId(payload.getJwtId());
					fuser.setName((String) payload.get("name"));
					// // Print user identifier
					// String userId = payload.getSubject();
					// System.out.println("User ID: " + userId);
					//
					// // Get profile information from payload
					// String email = payload.getEmail();
					// boolean emailVerified =
					// Boolean.valueOf(payload.getEmailVerified());
					// String name = (String) payload.get("name");
					// String pictureUrl = (String) payload.get("picture");
					// String locale = (String) payload.get("locale");
					// String familyName = (String) payload.get("family_name");
					// String givenName = (String) payload.get("given_name");
				} else {
					throw new IllegalArgumentException("id token cannot be verified");
				}
			} catch (Exception e) {

			}

		}
		return fuser;
	}

	public void socialLoginSystem(SocialLoginRequestBean bean, LoginResponseBean response) throws Exception {
		response.setStatus(AppHttpStatus.SUCCESS);

		SocialLoginResponse fuser = getSocialInfo(bean);

		MstUser user = userDao.findByEmailAndIsSysUserAndStatusAndProviderType(fuser.getEmail(), false, MstUser.ACTIVE,
				MstUser.PROVIDER_TYPE_FACEBOOK);
		if (user != null) {
			if (!CommonUtil.isEmpty(user.getAccessToken())) {
				response.setAccessToken(user.getAccessToken());
			} else {
				user.setAccessToken(UUID.randomUUID().toString());
				response.setAccessToken(user.getAccessToken());
				userDao.save(user);
			}
			return;
		} else {
			user = new MstUser();

			user.setUserName(fuser.getName());
			user.setEmail(fuser.getEmail());
			user.setProviderId(fuser.getId());

			user.setStatus(MstUser.ACTIVE);
			user.setIsSysUser(false);
			user.setProviderType(bean.getProviderType());
			user.setCreatedAt(new Date());
			user.setIsDeleted(false);
			user.setAccessToken(UUID.randomUUID().toString());
			response.setAccessToken(user.getAccessToken());

			userDao.save(user);

			MstUserProfile profile = new MstUserProfile();
			profile.setUserId(user.getId());
			profile.setCreatedBy(user.getId());
			profile.setUserName(user.getUserName());
			profile.setStatus(MstUser.ACTIVE);
			profile.setEmail(user.getEmail());
			profile.setCreatedAt(user.getCreatedAt());
			profile.setIsDeleted(user.getIsDeleted());
			profileRepo.save(profile);
		}
	}

	/**
	 * API 190
	 * 
	 * @author Danhloc
	 * @param request
	 * @param response
	 */
	// public void login(LoginRequestBean request, LoginResponseBean response) {
	//
	// MstUser user = userDao.findByEMailAndStatusAndIsDeleted(
	// request.getLoginId(), MstUser.USER_STATUS_1, false);
	// if (user == null) {
	// response.setStatus(AppHttpStatus.AUTH_FAILED);
	// return;
	// }
	//
	// // encode password
	// String password = CommonUtil.encryptPassword(request.getPassword(),
	// user.getSalt(), Integer.parseInt(environment.getProperty(
	// EnvironmentKey.SHA256_LOOPNUMBER_KEY.getKey())));
	//
	// // compare password and password data
	// if (password.equals(user.getPassword())) {
	// String token = UUID.randomUUID().toString();
	// user.setToken(token);
	// // check GenerateTokenFlg ice true
	// if (request.getGenerateTokenFlg()) {
	// String autologintoken = UUID.randomUUID().toString();
	// user.setAutoLoginToken(autologintoken);
	// response.setAccessToken(user.getToken());
	// response.setAutoLoginToken(user.getAutoLoginToken());
	// } else {
	// response.setAccessToken(user.getToken());
	// response.setAutoLoginToken(StringUtils.EMPTY);
	// }
	// userDao.save(user);
	// return;
	//
	// } else {
	// response.setStatus(AppHttpStatus.INTERNAL_SERVER_ERROR);
	// response.setAccessToken(StringUtils.EMPTY);
	// response.setAutoLoginToken(StringUtils.EMPTY);
	// return;
	// }
	//
	// }

	/**
	 * API 020 param request param response
	 * 
	 * @author Danhloc
	 * @param request
	 * @param response
	 * @throws Exception
	 */
	// public void resetPassword(ResetPasswordRequestBean request,
	// ResponseBean response) throws Exception {
	//
	// MstUser user = userDao.findByEMailAndStatusAndIsDeleted(
	// request.getLoginId(), MstUser.USER_STATUS_1, false);
	// if (user == null) {
	// response.setStatus(AppHttpStatus.AUTH_FAILED);
	// return;
	// }
	//
	// // compare temporarily in data
	// if (request.getTempPassword() != null && request.getTempPassword()
	// .equals(user.getTemporarilyPassword())) {
	//
	// // compare The current time is less than or equal to ExpirationAt
	// if (CommonUtil.getCurrentTime().before(user.getExpirationAt())) {
	//
	// // encode password
	// user.setPassword(CommonUtil.encryptPassword(
	// request.getNewPassword(), user.getSalt(),
	// Integer.parseInt(environment.getProperty(
	// EnvironmentKey.SHA256_LOOPNUMBER_KEY
	// .getKey()))));
	//
	// // update date and update updateBy
	// String updatedBy = user.getId();
	// user.setUpdatedBy(updatedBy);
	// user.setUpdatedAt(CommonUtil.getCurrentTime());
	//
	// // delete TemporarilyPassword and ExpirationAt
	// user.setTemporarilyPassword(StringUtils.EMPTY);
	// user.setExpirationAt(null);
	// userDao.save(user);
	// } else {
	// response.setStatus(AppHttpStatus.EXPIRED_TEMPORARY_PASSWORD);
	// return;
	// }
	//
	// } else {
	// response.setStatus(AppHttpStatus.TEMPORARY_PASSWORD_INVALID);
	// return;
	// }
	//
	// }

	/**
	 * API 030 param request param response
	 * 
	 * @author Danhloc
	 * @param request
	 * @param response
	 * @throws Exception
	 */
	// public void changePassword(ChangePasswordRequestBean request,
	// ChangePasswordResponseBean response) throws Exception {
	// MstUser user = checkTokenInSession(request);
	// if (user == null) {
	// response.setStatus(AppHttpStatus.AUTH_FAILED);
	// return;
	// } else {
	// // encode password
	// user.setPassword(CommonUtil.encryptPassword(
	// request.getNewPassword(), user.getSalt(),
	// Integer.parseInt(environment.getProperty(
	// EnvironmentKey.SHA256_LOOPNUMBER_KEY.getKey()))));
	// // update UpdatedAt and updateBy
	// user.setUpdatedAt(CommonUtil.getCurrentTime());
	// String updateBy = user.getId();
	// user.setUpdatedBy(updateBy);
	// userDao.save(user);
	// return;
	//
	// }
	// }

	/**
	 * 
	 * API 170 param request param response
	 * 
	 * @author thomc
	 * @param request
	 * @param response
	 * @throws Exception
	 */
	// public void sendMail(RequestBean request, ResponseBean response)
	// throws Exception {
	//
	// MstUser user = userDao.findByEMailAndStatusAndIsDeleted(
	// request.getLoginId(), MstUser.USER_STATUS_1, false);
	// if (user == null) {
	// response.setStatus(AppHttpStatus.AUTH_FAILED);
	// return;
	// }
	//
	// user.setExpirationAt(CommonUtil.getCurrentTime());
	// String temporarilyPassword = UUID.randomUUID().toString();
	// user.setTemporarilyPassword(temporarilyPassword);
	// userDao.save(user);
	//
	// try {
	// sendEmail("", "", "");
	// } catch (Exception e) {
	// logger.error("SendMailError", e);
	// throw e;
	// }
	//
	// }

	/**
	 * API 180
	 * 
	 * @author trann
	 * @param request
	 */

	// public void logOut(RequestBean request, ResponseBean response)
	// throws Exception {
	// MstUser mstUser = checkTokenInSession(request);
	//
	// if (mstUser == null) {
	// response.setStatus(AppHttpStatus.AUTH_FAILED);
	// return;
	// } else {
	// mstUser.setToken(null);
	// response.setStatus(AppHttpStatus.SUCCESS);
	// userDao.save(mstUser);
	// return;
	// }
	// }
	//
	// private void sendEmail(String fromAddr, String subject, String
	// contentSend)
	// throws Exception {
	// // get app param for email
	// String userName = environment.getProperty("mail.user");
	// String cc_em_pwd = environment.getProperty("mail.pwd");
	// String cc_em_host = environment.getProperty("mail.host");
	// String cc_em_port = environment.getProperty("mail.port");
	// String cc_em_auth = environment.getProperty("mail.auth");
	// String cc_em_ssl = environment.getProperty("mail.ssl ");
	//
	// try {
	// sendEMail(userName, cc_em_pwd, fromAddr, userName, cc_em_host,
	// cc_em_port, subject, contentSend.toString(), cc_em_auth,
	// cc_em_ssl);
	// } catch (Exception e) {
	// logger.error("SendEmail Error:", e);
	// throw e;
	// }
	// }
	//
	// public static boolean sendEMail(final String username,
	// final String password, String from, String to, String host,
	// String port, String subject, String body, String isAuthen,
	// String isSsl) throws Exception {
	// Properties props = new Properties();
	// props.put("mail.smtp.starttls.enable", isSsl);
	// props.put("mail.smtp.auth", isAuthen);
	// props.put("mail.smtp.host", host);
	// props.put("mail.smtp.port", port);
	//
	// Session session = Session.getDefaultInstance(props,
	// new javax.mail.Authenticator() {
	// protected PasswordAuthentication getPasswordAuthentication() {
	// return new PasswordAuthentication(username, password);
	// }
	// });
	//
	// try {
	//
	// Message message = new MimeMessage(session);
	// message.setFrom(new InternetAddress(from));
	// message.setRecipients(Message.RecipientType.TO,
	// InternetAddress.parse(to));
	// message.setSubject(MimeUtility.encodeText(subject, "utf-8", "B"));
	// message.setContent(body, "text/html;charset=UTF-8");
	//
	// Transport.send(message);
	//
	// System.out.println("Sent email Done");
	// return true;
	// } catch (MessagingException e) {
	// throw new RuntimeException(e);
	// }
	// }
	//

}
