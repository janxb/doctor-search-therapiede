package de.janbrodda.doctorsearchkvberlin;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DoctorSearchTherapiedeApplication {

	private static final WebClient webClient = new WebClient();
	private static final String baseUrl = "https://www.therapie.de";
	private static final int pageSize = 15;

	public static void main(String[] args) throws IOException {
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet sheet = workbook.createSheet();
		Row headerRow = sheet.createRow(sheet.getLastRowNum() + 1);
		headerRow.createCell(0).setCellValue("Name");
		headerRow.createCell(headerRow.getLastCellNum()).setCellValue("Email");
		headerRow.createCell(headerRow.getLastCellNum()).setCellValue("Phone");
		headerRow.createCell(headerRow.getLastCellNum()).setCellValue("Address");

		String search = "59065";
		String radius = "50";
		String mainUrl = baseUrl + "/therapeutensuche/ergebnisse/?ort=" + search + "&abrechnungsverfahren=7&search_radius=" + radius;

		//System.out.println(mainUrl);
		System.out.println("Fetching main search result page");
		Document mainDoc = Jsoup.parse(((HtmlPage) webClient.getPage(mainUrl)).asXml());

		int resultCount = Integer.parseInt(mainDoc.selectFirst("h5.subheader").text().split(" ")[3]);
		double pageCount = Math.ceil(resultCount / (double) pageSize);
		System.out.println("Found " + resultCount + " results, fetching up to page number " + (int) pageCount);

		List<Doctor> doctors = new ArrayList<>(getDoctorsForResultPage(mainDoc, 1, (int) pageCount));

		for (int i = 2; i <= pageCount; i++) {
			String pageUrl = mainUrl + "&page=" + i;
			System.out.println("Fetching result page " + i + " / " + (int) pageCount);
			var foundDoctors = getDoctorsForResultPage(Jsoup.parse(((HtmlPage) webClient.getPage(pageUrl)).asXml()), i, (int) pageCount);
			doctors.addAll(foundDoctors);
			if (foundDoctors.size() < pageSize) {
				System.out.println("Stopping execution after page " + i + ", rest of elements are base entries only");
				break;
			}
		}

		System.out.println(doctors);
		doctors.stream().
				filter(d -> d.getEmail() != null || d.getPhone() != null).forEach(d ->
																				  {
																					  Row row = sheet.createRow(sheet.getLastRowNum() + 1);
																					  row.createCell(0).setCellValue(d.getName());
																					  row.createCell(row.getLastCellNum()).setCellValue(d.getEmail());
																					  row.createCell(row.getLastCellNum()).setCellValue(d.getPhone());
																					  row.createCell(row.getLastCellNum()).setCellValue(d.getAddress());
																				  });

		FileOutputStream outputStream = new FileOutputStream("doctors_" + search + "_" + radius + "km.xlsx");
		workbook.write(outputStream);
		workbook.close();
	}

	static List<Doctor> getDoctorsForResultPage(Document mainDoc, int pageNum, int pageCount) throws IOException {
		List<Doctor> doctors = new ArrayList<>();
		int i = 0;
		for (Element searchResult : mainDoc.select(".search-results-list .panel-title")) {
			i++;
			Element detailsLink = searchResult.selectFirst("a");
			if (detailsLink == null) {
				break;
			}

			String detailsUrl = baseUrl + detailsLink.attr("href");
			//System.out.println(detailsUrl);
			System.out.println("Fetching details page " + i + " / " + pageSize + " of search result page " + pageNum + " / " + pageCount);
			HtmlPage detailsPage = webClient.getPage(detailsUrl);
			Document detailsDoc = Jsoup.parse(detailsPage.asXml());
			String phone = detailsDoc.selectFirst(".icon-phone a").text();
			String name = detailsDoc.selectFirst("div.therapist-name span[itemprop='name']").text();
			String street = detailsDoc.selectFirst("div[itemprop='address'] span[itemprop='streetAddress']").text();
			String postalCode = detailsDoc.selectFirst("div[itemprop='address'] span[itemprop='postalCode']").text();
			String city = detailsDoc.selectFirst("div[itemprop='address'] span[itemprop='addressLocality']").text();
			String email = null;
			try {
				email = (String) detailsPage.executeJavaScript("decryptString(contactEmail, -1)").getJavaScriptResult();
			} catch (Exception ignored) {
			}
			doctors.add(Doctor.builder()
							  .phone(phone)
							  .email(email)
							  .name(name)
							  .street(street)
							  .postalCode(postalCode)
							  .city(city)
							  .build()
			);
		}
		return doctors;
	}
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Doctor {
	private String email;
	private String phone;
	private String name;
	private String street;
	private String postalCode;
	private String city;

	public String getAddress() {
		return street + ", " + postalCode + " " + city;
	}
}
