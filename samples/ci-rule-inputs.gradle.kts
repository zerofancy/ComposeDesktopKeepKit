// Keep the selector's transitive-input inventory honest. This copies the actual
// plugin report; it does not resolve a second graph or alter production rules.
val captureCiRuleInputs = tasks.register<Copy>("captureCiRuleInputs") {
    dependsOn("generateKeepKitRules")
    from(provider {
        tasks.named("generateKeepKitRules").get().outputs.files.files.filter { it.name == "report.json" }
    })
    into(layout.buildDirectory.dir("verification/ci-inputs"))
    rename("report.json", "rule-inputs.json")
}
tasks.matching { it.name == "runRelease" }.configureEach { dependsOn(captureCiRuleInputs) }
