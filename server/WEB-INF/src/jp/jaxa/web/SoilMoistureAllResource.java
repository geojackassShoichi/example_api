/*
Copyright (c) 2013 jaxa

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package jp.jaxa.web;

import static java.lang.String.format;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Calendar;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

@Path("smcall")
public class SoilMoistureAllResource extends ApiResource {
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
			@DefaultValue("-9999") @QueryParam("lat") float latitude,
			@DefaultValue("-9999") @QueryParam("lon") float longitude,
			@DefaultValue("-9999.0") @QueryParam("date") String dateStr,
			@DefaultValue("0.1") @QueryParam("range") float range,
			@DefaultValue("callback") @QueryParam("callback") String callback) {
		if (isValidToken(token) == false) {
			return getFormattedError(Response.status(401), "Invalid Token.",
					format, callback);
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
			return getFormattedError(Response.status(406),
					"Invalid Parameter: \"date\", " + e.getLocalizedMessage()
							+ ".", format, callback);
		}

		try {
			Connection con = loadConnection();

			PreparedStatement statement = con
					.prepareStatement("SELECT lat,lon,smc FROM gcom_w1_data"
							+ " WHERE lat between ? and ? AND lon between ? and ? AND observed_at = ?"
							+ "AND smc > -9998");
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
			int rowCount = 0;
			if ("xml".equalsIgnoreCase(format)) {
				while (resultSet.next()) {
					data_entity = format(
							"%s"
									+ "<value><lat>%f</lat><lon>%f</lon><smc>%f</smc></value>",
							data_entity, resultSet.getFloat(1),
							resultSet.getFloat(2), resultSet.getFloat(3));
					rowCount++;
				}
			} else if ("json".equalsIgnoreCase(format)
					|| "jsonp".equalsIgnoreCase(format)) {
				if (resultSet.next()) {
					data_entity = format("{\"lat\":%f,\"lon\":%f,\"smc\":%f}",
							resultSet.getFloat(1), resultSet.getFloat(2),
							resultSet.getFloat(3));
					while (resultSet.next()) {
						data_entity = format("%s"
								+ ",{\"lat\":%f,\"lon\":%f,\"smc\":%f}",
								data_entity, resultSet.getFloat(1),
								resultSet.getFloat(2), resultSet.getFloat(3));
						rowCount++;
					}
				}
			} else {
				if (resultSet.next()) {
					data_entity = format("%f,%f,%f", resultSet.getFloat(1),
							resultSet.getFloat(2), resultSet.getFloat(3));
					while (resultSet.next()) {
						data_entity = format("%s,%f,%f,%f", data_entity,
								resultSet.getFloat(1), resultSet.getFloat(2),
								resultSet.getFloat(3));
						rowCount++;
					}
				}
			}

			con.close();

			if (rowCount == 0) {
				return getFormattedError(Response.status(406), NO_DATA_MESSAGE,
						format, callback);
			}

			return getFormattedResponse(Response.ok(), data_entity, format,
					callback);
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Response.ok().build();
	}

	/**
	 * @param builder
	 * @param retval
	 * @param format
	 * @return
	 */
	private Response getFormattedResponse(ResponseBuilder builder,
			String data_entity, String format, String callback) {
		if ("xml".equalsIgnoreCase(format)) {
			String entity = format(
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
							+ "<response><result>ok</result><values>%s</values></response>",
					data_entity);
			builder = builder.entity(entity);
			builder = builder.type(MediaType.TEXT_XML_TYPE);
		} else if ("json".equalsIgnoreCase(format)) {
			String entity = format("{\"result\":\"ok\",\"values\":[%s]}",
					data_entity);
			builder = builder.entity(entity);
			builder = builder.type(MediaType.APPLICATION_JSON_TYPE);
		} else if ("jsonp".equalsIgnoreCase(format)) {
			String entity = format("%s({\"result\":\"ok\",\"values\":[%s]})",
					callback, data_entity);
			builder = builder.entity(entity);
			builder = builder.type("application/javascript");
		} else {
			String entity = format("%s", data_entity);
			builder = builder.entity(entity);
		}
		builder = builder.encoding("utf-8");
		return builder.build();
	}
}
