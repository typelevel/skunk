function sourceUrlFix(sourceUrl) {
    $("#source-link").attr("href", sourceUrl.replace("target/mdoc", "src/main/paradox"))
}
