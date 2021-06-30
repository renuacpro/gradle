/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.configurationcache.isolated

import org.gradle.tooling.model.gradle.GradleBuild

class IsolatedProjectsToolingApiInvocationIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    def "caches creation of custom tooling model"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = fetchModel()

        then:
        model.message == "It works from project :"

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        outputContains("creating model for root project 'root'")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"

        and:
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("creating model for root project 'root'")
        outputContains("Configuration cache entry reused.")

        when:
        buildFile << """
            // some change
        """

        executer.withArguments(ENABLE_CLI)
        def model3 = fetchModel()

        then:
        model3.message == "It works from project :"

        and:
        outputContains("Creating tooling model as configuration cache cannot be reused because file 'build.gradle' has changed.")
        outputContains("creating model for root project 'root'")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")
    }

    def "can ignore problems and cache custom model"() {
        given:
        settingsFile << """
            include('a')
            include('b')
        """
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            allprojects {
                plugins.apply('java-library')
            }
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model = fetchModel()

        then:
        model.message == "It works from project :"
        problems.assertResultHasProblems(result) {
            withUniqueProblems(
                "Build file 'build.gradle': Cannot access project ':a' from project ':'",
                "Build file 'build.gradle': Cannot access project ':b' from project ':'",
            )
        }
        result.assertHasPostBuildOutput("Configuration cache entry stored with 2 problems.")

        when:
        executer.withArguments(ENABLE_CLI, WARN_PROBLEMS_CLI_OPT)
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"
        outputContains("Reusing configuration cache.")
        outputContains("Configuration cache entry reused.")
    }

    def "caches calculation of GradleBuild model"() {
        given:
        settingsFile << """
            include("a")
            include("b")
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = fetchModel(GradleBuild)

        then:
        model.rootProject.name == "root"
        model.projects.size() == 3
        model.projects[0].name == "root"
        model.projects[1].name == "a"
        model.projects[2].name == "b"

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = fetchModel(GradleBuild)

        then:
        model2.rootProject.name == "root"
        model2.projects.size() == 3
        model2.projects[0].name == "root"
        model2.projects[1].name == "a"
        model2.projects[2].name == "b"

        and:
        outputContains("Reusing configuration cache.")
        outputContains("Configuration cache entry reused.")
    }

    def "caches execution of BuildAction that queries custom tooling model"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            plugins.apply(my.MyPlugin)
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("creating model")
        outputContains("Configuration cache entry reused.")

        when:
        buildFile << """
            // some change
        """

        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchCustomModelForEachProject())

        then:
        model3.size() == 2
        model3[0].message == "It works from project :"
        model3[1].message == "It works from project :a"

        and:
        outputContains("Creating tooling model as configuration cache cannot be reused because file 'build.gradle' has changed.")
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")
    }

    def "caches execution of phased BuildAction that queries custom tooling model"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            plugins.apply(my.MyPlugin)
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def models = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject())

        then:
        def messages = models.left
        messages.size() == 2
        messages[0] == "It works from project :"
        messages[1] == "It works from project :a"
        def model = models.right
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")

        when:
        executer.withArguments(ENABLE_CLI)
        def models2 = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject())

        then:
        def messages2 = models2.left
        messages2.size() == 2
        messages2[0] == "It works from project :"
        messages2[1] == "It works from project :a"
        def model2 = models2.right
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("creating model")
        outputContains("Configuration cache entry reused.")

        when:
        buildFile << """
            // some change
        """

        executer.withArguments(ENABLE_CLI)
        def models3 = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject())

        then:
        def messages3 = models3.left
        messages3.size() == 2
        messages3[0] == "It works from project :"
        messages3[1] == "It works from project :a"
        def model3 = models3.right
        model3.size() == 2
        model3[0].message == "It works from project :"
        model3[1].message == "It works from project :a"

        and:
        outputContains("Creating tooling model as configuration cache cannot be reused because file 'build.gradle' has changed.")
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")
    }

    def "caches execution of phased BuildAction that queries custom tooling model and that may, but does not actually, run tasks"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            plugins.apply(my.MyPlugin)
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def models = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject()) {
            // Empty list means "run tasks defined by build logic or default task"
            forTasks([])
        }

        then:
        def messages = models.left
        messages.size() == 2
        messages[0] == "It works from project :"
        messages[1] == "It works from project :a"
        def model = models.right
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")

        when:
        executer.withArguments(ENABLE_CLI)
        def models2 = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject()) {
            forTasks([])
        }

        then:
        def messages2 = models2.left
        messages2.size() == 2
        messages2[0] == "It works from project :"
        messages2[1] == "It works from project :a"
        def model2 = models2.right
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("creating model")
        result.assertHasPostBuildOutput("Configuration cache entry reused.")
    }

    def "caches execution of phased BuildAction that queries custom tooling model and that runs tasks"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            include("a")
            include("b")
        """
        buildFile << """
            plugins.apply(my.MyPlugin)
        """
        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            task thing { }
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def models = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject()) {
            forTasks(["thing"])
        }

        then:
        def messages = models.left
        messages.size() == 2
        messages[0] == "It works from project :"
        messages[1] == "It works from project :a"
        def model = models.right
        model.size() == 2
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"

        and:
        outputContains("Creating tooling model as no configuration cache is available for the requested model")
        outputContains("creating model for root project 'root'")
        outputContains("creating model for project ':a'")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")
        result.ignoreBuildSrc.assertTasksExecuted(":a:thing")

        when:
        executer.withArguments(ENABLE_CLI)
        def models2 = runPhasedBuildAction(new FetchPartialCustomModelForEachProject(), new FetchCustomModelForEachProject()) {
            forTasks(["thing"])
        }

        then:
        def messages2 = models2.left
        messages2.size() == 2
        messages2[0] == "It works from project :"
        messages2[1] == "It works from project :a"
        def model2 = models2.right
        model2.size() == 2
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"

        and:
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("creating model")
        result.assertHasPostBuildOutput("Configuration cache entry reused.")
        result.ignoreBuildSrc.assertTasksExecuted(":a:thing")
    }
}
