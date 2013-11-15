package jp.jaxa.web;

import static java.lang.String.format;
import static java.sql.DriverManager.getConnection;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

@Path("ocean_temperature_all")
public class OceanTemperatureAllResource {
	public static DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	@Context
	ServletContext context;

	/**
	 * @param token
	 * @param format
	 * @param latitude
	 * @param longitude
	 * @param dateStr
	 * @return
	 */
	@GET
	public Response getIt(@QueryParam("token") String token,
			@DefaultValue("xml") @QueryParam("format") String format,
			@DefaultValue("-19") @QueryParam("lat") float latitude,
			@DefaultValue("24") @QueryParam("lon") float longitude,
			@DefaultValue("2012-08-01") @QueryParam("date") String dateStr,
			@DefaultValue("1") @QueryParam("range") float range) {
		if (isValidToken(token) == false) {
			return getFormattedError(Response.status(401), "Invalid Token.",
					format);
		}

		Date observedAt;
		try {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(DATE_FORMAT.parse(dateStr));
			calendar.set(Calendar.HOUR_OF_DAY, 0);
			calendar.set(Calendar.MINUTE, 0);
			calendar.set(Calendar.SECOND, 0);
			calendar.set(Calendar.MILLISECOND, 0);
			observedAt = new Date(calendar.getTimeInMillis());
		} catch (ParseException e) {
			return getFormattedError(
					Response.status(406),
					"Invalid Parameter: \"date\", You must specify \"yyyy-MM-dd\" for the parameter.",
					format);
		}

		try {
			Connection con = loadConnection();

			PreparedStatement statement = con
					.prepareStatement("SELECT lat,lon,sst FROM gcom_w1_data"
							+ " WHERE lat between ? and ? AND lon between ? and ? AND observed_at = ?");
			float lowerlat = latitude - range;
			float upperlat = latitude + range;
			float lowerlon = longitude - range;
			float upperlon = longitude + range;

			statement.setDouble(1, lowerlat);
			statement.setDouble(2, upperlat);
			statement.setDouble(3, lowerlon);
			statement.setDouble(4, upperlon);
			statement.setDate(5, observedAt);

			String data_entity = "";
			ResultSet resultSet = statement.executeQuery();
			while (resultSet.next()) {
				data_entity = format("%s" + "%f,%f,%f,", data_entity,
						resultSet.getFloat(1), resultSet.getFloat(2),
						resultSet.getFloat(3));
			}
			return getFormattedResponse(Response.ok(), data_entity, format);
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Response.ok().build();
	}

	/**
	 * 指定されたトークンが正しいものかどうかを判定する
	 * 
	 * @param token
	 * @return
	 */
	private boolean isValidToken(String token) {
		if (token == null) {
			return false;
		}

		try {
			Connection con = loadConnection();
			PreparedStatement statement = con
					.prepareStatement("SELECT EXISTS (SELECT token FROM tokens WHERE token = ?)");
			statement.setString(1, token);

			ResultSet resultSet = statement.executeQuery();
			while (resultSet.next()) {
				return resultSet.getBoolean(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * @param builder
	 * @param retval
	 * @param format
	 * @return
	 */
	private Response getFormattedResponse(ResponseBuilder builder,
			String data_entity, String format) {
		if ("xml".equalsIgnoreCase(format)) {
			String entity = format(
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
							+ "<response><result>ok</result><ocean_temperature>%s</ocean_temperature></response>",
					data_entity);
			builder = builder.entity(entity);
			builder = builder.type(MediaType.TEXT_XML_TYPE);
		} else if ("json".equalsIgnoreCase(format)) {
			String entity = format(
					"{\"result\": \"ok\", \"ocean_temperature\": %s}",
					data_entity);
			builder = builder.entity(entity);
			builder = builder.type(MediaType.APPLICATION_JSON_TYPE);
		} else {
			String entity = format("%s", data_entity);
			builder = builder.entity(entity);
		}
		builder = builder.encoding("utf-8");
		return builder.build();
	}

	/**
	 * @param builder
	 * @param message
	 * @param format
	 * @return
	 */
	private Response getFormattedError(ResponseBuilder builder, String message,
			String format) {
		if ("xml".equalsIgnoreCase(format)) {
			String entity = format(
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
							+ "<response><result>error</result><message>%s</message></response>",
					message);
			builder = builder.entity(entity);
			builder = builder.type(MediaType.TEXT_XML_TYPE);
		} else if ("json".equalsIgnoreCase(format)) {
			String entity = format(
					"{\"result\": \"error\", \"message\": \"%s\"}",
					message.replaceAll("\"", "\\\""));
			builder = builder.entity(entity);
			builder = builder.type(MediaType.APPLICATION_JSON_TYPE);
		} else {
			builder = builder.entity(message);
		}
		builder = builder.encoding("utf-8");
		return builder.build();
	}

	/**
	 * データベースへの接続情報を設定ファイルから取得する
	 * 
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 */
	private Connection loadConnection() throws IOException, SQLException {
		Properties prop = new Properties();
		prop.load(context.getResourceAsStream("WEB-INF/conf/gcom_w1_db.ini"));

		String host = prop.getProperty("hostname");
		String port = prop.getProperty("port");
		String db = prop.getProperty("database");
		String user = prop.getProperty("user");
		String password = prop.getProperty("password");

		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException e) {
		}

		String url = format("jdbc:postgresql://%s:%s/%s", host, port, db);
		return getConnection(url, user, password);
	}
}