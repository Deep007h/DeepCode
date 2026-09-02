import re

file_path = "/home/deep/Documents/opencode/app/src/main/java/ai/deepcode/android/service/tools/ToolExecutor.kt"
with open(file_path, "r") as f:
    content = f.read()

# We want to match cases like "notion_search" -> { ... } and wrap the body in try-catch.
# A case might look like:
#                 "notion_search" -> {
#                     val svc = notionService ?: return "Notion not connected. Connect your Notion integration token first."
#                     ...
#                 }
#
# But we only want to wrap the actual action, or the whole block except the "val svc = ..." checks?
# Wrapping the whole body of the case is easiest.

tools_to_wrap = [
    "notion_search", "notion_read", "notion_list_databases",
    "github_list_repos", "github_list_contents", "github_read_file", "github_write_file",
    "github_create_branch", "github_list_branches", "github_list_prs", "github_create_pr",
    "github_list_issues", "github_create_issue", "github_search_code", "github_create_repo",
    "github_delete_repo", "github_check_workflow", "github_download_artifact",
    "drive_list", "drive_search", "drive_read", "drive_upload",
    "webbridge_agent"
]

import sys
lines = content.split('\n')
new_lines = []
in_target_block = False
brace_count = 0
block_lines = []
current_tool = ""

for line in lines:
    if not in_target_block:
        match = re.match(r'(\s+)"([^"]+)"\s*->\s*\{', line)
        if match and match.group(2) in tools_to_wrap:
            in_target_block = True
            brace_count = 1
            current_tool = match.group(2)
            block_lines = [line]
        else:
            new_lines.append(line)
    else:
        block_lines.append(line)
        brace_count += line.count('{') - line.count('}')
        if brace_count == 0:
            in_target_block = False
            # We have the whole block.
            # Block structure:
            # indent "tool" -> {
            #    body
            # }
            # Let's wrap the body in try/catch.
            indent_match = re.match(r'^(\s+)', block_lines[0])
            base_indent = indent_match.group(1) if indent_match else "                "
            
            body_indent = base_indent + "    "
            
            wrapped_block = []
            wrapped_block.append(block_lines[0])
            wrapped_block.append(body_indent + "try {")
            for b_line in block_lines[1:-1]:
                wrapped_block.append("    " + b_line if b_line.strip() else b_line)
            
            error_prefix = f"{current_tool} failed"
            wrapped_block.append(body_indent + "} catch (e: Exception) {")
            wrapped_block.append(body_indent + "    \"" + error_prefix + ": ${e.message}\"")
            wrapped_block.append(body_indent + "}")
            wrapped_block.append(block_lines[-1])
            
            new_lines.extend(wrapped_block)

with open(file_path, "w") as f:
    f.write('\n'.join(new_lines))

print("Modified ToolExecutor.kt successfully.")

