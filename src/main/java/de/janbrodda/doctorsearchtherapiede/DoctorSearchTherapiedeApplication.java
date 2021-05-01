package de.janbrodda.doctorsearchtherapiede;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
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
import java.util.concurrent.atomic.AtomicInteger;

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

		String search = "59494";
		String radius = "25";
		String mainUrl = baseUrl + "/therapeutensuche/ergebnisse/?ort=" + search + "&abrechnungsverfahren=7&search_radius=" + radius;

		System.out.println("Starting doctors search for location '" + search + "' with radius " + radius + "km...");

		List<Doctor> doctors = new ArrayList<>();
		int currentPageNum = 0;
		int maxPageNum = 0;
		do {
			currentPageNum++;
			String pageUrl = mainUrl + "&page=" + currentPageNum;
			Document document = Jsoup.parse(((HtmlPage) webClient.getPage(pageUrl)).asXml());

			if (currentPageNum == 1) {
				int resultCount = Integer.parseInt(document.selectFirst("h5.subheader").text().split(" ")[3]);
				maxPageNum = (int) Math.ceil(resultCount / (double) pageSize);
				System.out.println("Found " + resultCount + " results, fetching up to page number " + maxPageNum);
			}

			System.out.println("Processing result page " + currentPageNum + " / " + (maxPageNum > 0 ? maxPageNum : "?"));
			var foundDoctors = getDoctorsForResultPage(document, currentPageNum, maxPageNum);
			doctors.addAll(foundDoctors.getDoctors());
			if (foundDoctors.isBaseEntriesReached()) {
				System.out.println("Stopping execution after page " + currentPageNum + ", rest of elements are base entries only");
				break;
			}
		} while (currentPageNum < maxPageNum);

		AtomicInteger validDoctorCount = new AtomicInteger();
		doctors.stream().filter(d -> d.getEmail() != null || d.getPhone() != null).forEach(d -> {
			validDoctorCount.getAndIncrement();
			Row row = sheet.createRow(sheet.getLastRowNum() + 1);
			row.createCell(0).setCellValue(d.getName());
			row.createCell(row.getLastCellNum()).setCellValue(d.getEmail());
			row.createCell(row.getLastCellNum()).setCellValue(d.getPhone());
			row.createCell(row.getLastCellNum()).setCellValue(d.getAddress());
		});

		FileOutputStream outputStream = new FileOutputStream("doctors_" + search + "_" + radius + "km.xlsx");
		workbook.write(outputStream);
		workbook.close();

		System.out.println("Finished! Found " + doctors.size() + " results, of which " + validDoctorCount.get() + " have valid contact details.");
	}

	static SearchResult getDoctorsForResultPage(Document mainDoc, int pageNum, int pageCount) throws IOException {
		SearchResult.SearchResultBuilder searchResultBuilder = new SearchResult.SearchResultBuilder();
		searchResultBuilder.baseEntriesReached(mainDoc.selectFirst("div.radius") != null);

		int i = 0;
		for (Element searchResult : mainDoc.select(".search-results-list .panel-title")) {
			i++;
			Element detailsLink = searchResult.selectFirst("a");
			if (detailsLink == null) {
				System.out.println("Skipping details page " + i + " / " + pageSize + " of search result page " + pageNum + " / " + pageCount);
				continue;
			}

			String detailsUrl = baseUrl + detailsLink.attr("href");
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
			searchResultBuilder.doctor(Doctor.builder().phone(phone).email(email).name(name).street(street).postalCode(postalCode).city(city).build());
		}
		return searchResultBuilder.build();
	}
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SearchResult {
	private boolean baseEntriesReached;
	@Singular
	private List<Doctor> doctors;
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
