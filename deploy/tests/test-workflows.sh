#!/usr/bin/env bash
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
deploy="$(cd "$here/.." && pwd)"
root="$(cd "$deploy/.." && pwd)"
. "$here/lib.sh"

workflows="$root/.github/workflows"
ci="$workflows/ci.yml"
mobile_build="$workflows/mobile-build.yml"
mobile_update="$workflows/mobile-update.yml"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cat >"$work/inspect.rb" <<'RUBY'
require "yaml"

def load(path)
  YAML.safe_load(File.read(path), aliases: true)
end

def triggers(doc)
  doc.key?("on") ? doc["on"] : doc[true]
end

def steps_of(job)
  job["steps"] || []
end

def step_text(step)
  [step["run"], step["with"].to_a.flatten.join(" "), step["env"].to_a.flatten.join(" ")].compact.join(" ")
end

def smoke_index(steps)
  steps.index { |step| step["run"].to_s.include?("deploy/tests/smoke.sh") }
end

def fail_with(message)
  warn message
  exit 1
end

command = ARGV.shift
files = ARGV

case command
when "parses"
  load(files[0]).fetch("jobs").fetch("publish")
  exit 0
when "publish-needs"
  needs = Array(load(files[0]).dig("jobs", "publish", "needs")).sort
  exit(needs == %w[app backend contract] ? 0 : 1)
when "publish-condition"
  condition = load(files[0]).dig("jobs", "publish", "if").to_s
  ok = condition.include?("github.event_name == 'push'") && condition.include?("github.ref == 'refs/heads/master'")
  exit(ok ? 0 : 1)
when "publish-permissions"
  doc = load(files[0])
  publish = doc.dig("jobs", "publish", "permissions")
  others = doc["jobs"].reject { |name, _| name == "publish" }.values.map { |job| job["permissions"] }
  ok = publish == { "contents" => "read", "packages" => "write" } &&
       doc["permissions"] == { "contents" => "read" } &&
       others.none? { |permissions| permissions.to_h["packages"] }
  exit(ok ? 0 : 1)
when "publish-concurrency"
  concurrency = load(files[0]).dig("jobs", "publish", "concurrency")
  exit(concurrency.is_a?(Hash) && concurrency["cancel-in-progress"] == false ? 0 : 1)
when "smoke-before-publication"
  steps = steps_of(load(files[0]).dig("jobs", "publish"))
  smoke = smoke_index(steps)
  fail_with("falta el paso del humo") if smoke.nil?
  uses_compose = steps[smoke]["run"].to_s
  before = steps[0...smoke]
  pushes_early = before.any? do |step|
    step["with"].to_h["push"].to_s == "true" || step["run"].to_s =~ /docker push|imagetools/ ||
      step_text(step).include?("production") || step["uses"].to_s.include?("docker/login-action")
  end
  exit(pushes_early || uses_compose.empty? ? 1 : 0)
when "publication-after-smoke"
  steps = steps_of(load(files[0]).dig("jobs", "publish"))
  smoke = smoke_index(steps)
  fail_with("falta el paso del humo") if smoke.nil?
  after = steps[(smoke + 1)..] || []
  login = after.any? { |step| step["uses"].to_s.include?("docker/login-action") && step["with"].to_h["registry"] == "ghcr.io" }
  texts = after.map { |step| step_text(step) }.join("\n")
  ok = login && texts.include?("sha-") && texts.include?(":production") && texts.include?("docker push") &&
       texts.include?("ghcr.io/escumartin/neocatecumenal-$image") && texts.include?("backend web") &&
       texts.index("sha-") < texts.index(":production")
  exit(ok ? 0 : 1)
when "build-with-revision"
  steps = steps_of(load(files[0]).dig("jobs", "publish"))
  builds = steps.select { |step| step["uses"].to_s.include?("docker/build-push-action") }
  ok = builds.size == 2 &&
       builds.all? { |step| step["with"].to_h["load"].to_s == "true" && step["with"].to_h["push"].to_s != "true" } &&
       builds.all? { |step| step["with"].to_h["cache-from"].to_s.include?("type=gha") } &&
       builds.all? { |step| step["with"].to_h["cache-to"].to_s.include?("type=gha") } &&
       builds.any? { |step| step["with"].to_h["build-args"].to_s.include?("SERVICE_REVISION=${{ github.sha }}") }
  exit(ok ? 0 : 1)
when "actions-pinned-with-version"
  offenders = []
  files.each do |path|
    load(path)["jobs"].each do |job_name, job|
      steps_of(job).each do |step|
        next unless step["uses"]
        next if step["uses"].start_with?("./")
        pinned = step["uses"] =~ /\A[\w.-]+\/[\w.\/-]+@[0-9a-f]{40}\z/
        versioned = step["name"].to_s =~ /\d+\.\d+\.\d+/
        offenders << "#{File.basename(path)}:#{job_name}:#{step["uses"]}" unless pinned && versioned
      end
    end
  end
  warn offenders.join("\n") unless offenders.empty?
  exit(offenders.empty? ? 0 : 1)
when "only-allowed-secrets"
  allowed = ARGV.shift.split(",")
  files = ARGV
  used = files.flat_map { |path| File.read(path).scan(/secrets\.([A-Za-z0-9_]+)/).flatten }.uniq
  exit((used - allowed).empty? ? 0 : 1)
when "no-literal-credentials"
  patterns = [/ghp_[A-Za-z0-9]{20,}/, /github_pat_[A-Za-z0-9_]{20,}/, /-----BEGIN [A-Z ]*PRIVATE KEY-----/,
              /expo-[A-Za-z0-9]{30,}/, /AKIA[0-9A-Z]{16}/]
  hit = files.any? { |path| patterns.any? { |pattern| File.read(path) =~ pattern } }
  exit(hit ? 1 : 0)
when "dispatch-only"
  ok = files.all? { |path| triggers(load(path)).keys == ["workflow_dispatch"] }
  exit(ok ? 0 : 1)
when "read-only-permissions"
  ok = files.all? do |path|
    doc = load(path)
    doc["permissions"] == { "contents" => "read" } && doc["jobs"].values.none? { |job| job.key?("permissions") }
  end
  exit(ok ? 0 : 1)
when "expo-token-from-secret"
  ok = files.all? do |path|
    text = File.read(path)
    lines = text.lines.select { |line| line.include?("EXPO_TOKEN") }
    lines.size >= 1 && lines.all? { |line| line.include?("${{ secrets.EXPO_TOKEN }}") }
  end
  exit(ok ? 0 : 1)
when "no-inputs-in-run"
  offenders = files.flat_map do |path|
    load(path)["jobs"].flat_map do |_, job|
      steps_of(job).select { |step| step["run"].to_s.include?("${{") }.map { |step| step["run"] }
    end
  end
  warn offenders.join("\n") unless offenders.empty?
  exit(offenders.empty? ? 0 : 1)
when "eas-commands"
  build = steps_of(load(files[0])["jobs"]["build"]).map { |step| step["run"].to_s }.join("\n")
  update = steps_of(load(files[1])["jobs"]["update"]).map { |step| step["run"].to_s }.join("\n")
  ok = build.include?("eas build") && build.include?("eas submit") && update.include?("eas update") &&
       update.include?("--channel production")
  exit(ok ? 0 : 1)
else
  fail_with("orden desconocida: #{command}")
end
RUBY

inspect_workflows() {
  ruby "$work/inspect.rb" "$@"
}

all_workflows=("$workflows"/*.yml)
mobile_workflows=("$mobile_build" "$mobile_update")

for file in "$ci" "$mobile_build" "$mobile_update"; do
  if [ ! -f "$file" ]; then
    echo "FALLO: falta $file"
    exit 1
  fi
done

check "ci.yml es YAML válido y define el job publish" inspect_workflows parses "$ci"
check "publish necesita backend, contract y app" inspect_workflows publish-needs "$ci"
check "publish solo corre en push a master" inspect_workflows publish-condition "$ci"
check "publish serializa sus ejecuciones sin cancelar las en curso" inspect_workflows publish-concurrency "$ci"
check "packages: write solo en publish y el resto en solo lectura" inspect_workflows publish-permissions "$ci"
check "publish construye backend y web con SERVICE_REVISION, sin subirlas, con caché de GHA" inspect_workflows build-with-revision "$ci"
check "el humo de compose va antes de iniciar sesión, etiquetar o subir nada" inspect_workflows smoke-before-publication "$ci"
check "sha-<commit> y production se publican solo tras el humo, con login en GHCR" inspect_workflows publication-after-smoke "$ci"
check "todas las actions están fijadas por SHA con la versión en el name del paso" inspect_workflows actions-pinned-with-version "${all_workflows[@]}"
check "ci.yml solo usa GITHUB_TOKEN como secreto" inspect_workflows only-allowed-secrets GITHUB_TOKEN "$ci"
check "los workflows móviles solo usan EXPO_TOKEN como secreto" inspect_workflows only-allowed-secrets EXPO_TOKEN "${mobile_workflows[@]}"
check "ningún workflow lleva credenciales en claro" inspect_workflows no-literal-credentials "${all_workflows[@]}"
check "los workflows móviles se disparan solo con workflow_dispatch" inspect_workflows dispatch-only "${mobile_workflows[@]}"
check "los workflows móviles tienen solo permisos de lectura" inspect_workflows read-only-permissions "${mobile_workflows[@]}"
check "EXPO_TOKEN solo se referencia como secreto" inspect_workflows expo-token-from-secret "${mobile_workflows[@]}"
check "los workflows móviles no interpolan expresiones en run" inspect_workflows no-inputs-in-run "${mobile_workflows[@]}"
check "los workflows móviles lanzan eas build, submit y update a production" inspect_workflows eas-commands "${mobile_workflows[@]}"

finish
