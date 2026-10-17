// Standalone ProGuard fixtures explicitly choose fragments instead of applying
// the plugin. Record the very same fragment list for the CI selection guard.
val ciRuleFragments = extra["ciRuleFragments"] as List<*>
val ciRuleInputs = layout.buildDirectory.file("verification/ci-inputs/rule-inputs.json")
val captureCiRuleInputs = tasks.register("captureCiRuleInputs") {
    inputs.property("rules", ciRuleFragments)
    outputs.file(ciRuleInputs)
    doLast {
        ciRuleInputs.get().asFile.apply {
            parentFile.mkdirs()
            writeText(groovy.json.JsonOutput.toJson(mapOf("schemaVersion" to 1,
                "applications" to listOf(mapOf("rules" to ciRuleFragments)))) + "\n")
        }
    }
}
tasks.matching { it.name in setOf("shrinkSmoke", "shrinkRegression") }.configureEach {
    dependsOn(captureCiRuleInputs)
}
