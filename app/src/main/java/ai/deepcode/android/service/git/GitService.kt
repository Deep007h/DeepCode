package ai.deepcode.android.service.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

class GitService {
    fun getGitStatus(projectPath: String): GitInfo {
        val repoDir = File(projectPath, ".git")
        if (!repoDir.exists() || !repoDir.isDirectory) {
            return GitInfo(isRepo = false, branch = "", modifiedFiles = emptyList(), recentCommits = emptyList())
        }

        return try {
            val builder = FileRepositoryBuilder()
            val repository = builder.setGitDir(repoDir)
                .readEnvironment()
                .findGitDir()
                .build()

            repository.use { repo ->
                Git(repo).use { git ->
                    val branch = repo.branch ?: "unknown"

                    val status = git.status().call()
                    val modified = mutableListOf<String>()
                    modified.addAll(status.modified)
                    modified.addAll(status.added)
                    modified.addAll(status.untracked)
                    modified.addAll(status.removed)

                    val commits = mutableListOf<String>()
                    try {
                        val log = git.log().setMaxCount(5).call()
                        for (commit in log) {
                            val authorName = commit.authorIdent.name ?: "Unknown"
                            commits.add("${commit.name.substring(0, 7)} - ${commit.shortMessage} (by $authorName)")
                        }
                    } catch (e: Exception) {
                        commits.add("No commits yet")
                    }

                    GitInfo(
                        isRepo = true,
                        branch = branch,
                        modifiedFiles = modified,
                        recentCommits = commits
                    )
                }
            }
        } catch (e: Exception) {
            GitInfo(
                isRepo = false,
                branch = "",
                modifiedFiles = emptyList(),
                recentCommits = listOf("Git error: ${e.message}")
            )
        }
    }
}

data class GitInfo(
    val isRepo: Boolean,
    val branch: String,
    val modifiedFiles: List<String>,
    val recentCommits: List<String>
)
