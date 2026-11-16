# ADR-008: Production Readiness & Regulated Banking Architecture Roadmap

## Status
Accepted (2026-11-15).

## Context
The Commission Quote Application is currently structured as an engineering take-home MVP deployed locally via Docker Compose.
To transition this system into a live regulated financial institution (e.g. an APRA-regulated ADI subject to CPS 234 Information Security, CPS 232 Business Continuity Management, and PCI-DSS), the architecture must elevate from a local multi-container bridge environment into a Zero-Trust, highly available, secure banking platform.

## Decision & Production Roadmap

### 1. Transport Layer Security & Zero-Trust Inter-Service Network (mTLS)
* **Current State:** Plaintext HTTP over Docker's internal virtual bridge network (`app-network`).
* **Production Architecture:**
  * Enforce **Mutual TLS (mTLS 1.3)** for all inter-service communications (`frontend &rarr; backend` and `backend &rarr; vendor`).
  * Implement service identity via short-lived X.509 certificates managed by a Service Mesh (e.g. **Istio** / **Linkerd**) or native Spring Boot SSL bundles backed by an internal Public Key Infrastructure (PKI / AWS Private CA).
  * Require TLS 1.3 cipher suites; disable legacy TLS 1.0, 1.1, and 1.2.

### 2. Network Topology & Ingress Isolation (VPC Private Subnets)
* **Current State:** Ports 8000 (backend) and 8001 (mock vendor) are published to the host for local development convenience.
* **Production Architecture:**
  * Strict multi-tier VPC network topology across three Availability Zones (Multi-AZ).
  * **Public Subnet:** Hosts only the public-facing Application Load Balancer (ALB) on port 443.
  * **Private App Subnet:** Hosts the backend quotation service with **zero public ingress**. Security groups allow ingress strictly from the ALB security group on port 8000.
  * **Isolated Data/Vendor Subnet:** Outbound egress to partner vendor APIs traverses an AWS NAT Gateway / VPC Endpoint through explicit egress firewall rules.

### 3. Centralized Secrets Management & Automated Rotation
* **Current State:** Credentials (`VENDOR_API_KEY`) are injected via environment variables sourced from `.env` files.
* **Production Architecture:**
  * Eliminate `.env` files entirely.
  * Secrets reside in **AWS Secrets Manager** or **HashiCorp Vault** with envelope encryption backed by AWS KMS Customer Managed Keys (CMKs).
  * Enable automated 30-day credential rotation with zero-downtime dual-key validation windows.
  * Ephemeral IAM Roles for Service Accounts (IRSA on EKS or ECS Task Roles) eliminate long-lived cloud credentials.

### 4. Edge Protection, WAF & Anti-Abuse Controls
* **Current State:** Nginx applies browser headers, client body caps, and basic reverse proxy routing.
* **Production Architecture:**
  * Front the application with **Cloudflare** or **AWS WAF**.
  * Enforce rate limiting per client IP (e.g. max 20 quotes/minute) to mitigate Denial-of-Wallet and automated scraping attacks.
  * Apply AWS WAF Core Rule Set (CRS) to detect SQLi, XSS, and payload anomalies before reaching the application layer.
  * Bot control / CAPTCHA challenge on suspect traffic fingerprints.

### 5. Audit Logging, Compliance & SIEM Integration
* **Current State:** Standard application logging to stdout.
* **Production Architecture:**
  * Emit structured JSON logs containing correlation IDs (`traceId`, `spanId`) via OpenTelemetry (OTel).
  * Mask sensitive customer data and loan amounts in compliance with privacy regulations.
  * Real-time audit log streaming to an immutable logging bucket (WORM - Write Once, Read Many) with retention policies adhering to banking statutory requirements (e.g. 7 years).
  * Integration with enterprise SIEM (Splunk / Datadog / AWS Security Lake) with automated alerting on elevated HTTP 401/403 rates or unusual loan quote volume spikes.

### 6. Supply Chain Security, SBOM & Container Signing
* **Current State:** Maven plugins enforce SpotBugs, Spotless, and JaCoCo; OWASP Dependency-Check runs during verification.
* **Production Architecture:**
  * Transition from Alpine JRE to true **Distroless runtime images** (`gcr.io/distroless/java25:nonroot` or Chainguard Wolfi) in Kubernetes, utilizing external kubelet `httpGet` probes for liveness/readiness to eliminate all in-container package managers and shells.
  * Automated Software Bill of Materials (SBOM) generation (CycloneDX / SPDX) during CI/CD.
  * Container image signing using **Cosign / Sigstore**; Kubernetes admission controllers (e.g. Kyverno) reject unsigned images.
  * Continuous container vulnerability scanning via Amazon ECR / Trivy / Snyk blocking deployment of images with CVE severity &ge; HIGH.
  * Automated Dependabot / Renovate pull requests with automated test execution.

### 7. High Availability, Scalability & Disaster Recovery (DR)
* **Current State:** Single-container instances per service.
* **Production Architecture:**
  * Container orchestration on AWS EKS / ECS Fargate with Horizontal Pod Autoscaling (HPA) targeting 70% CPU/Memory utilization.
  * Active-Active multi-AZ deployment guaranteeing 99.99% availability.
  * Circuit breaker state synchronization or distributed metrics tracking via Redis / Prometheus to maintain fleet-wide partner health views.
  * Defined Recovery Time Objective (RTO &le; 15 minutes) and Recovery Point Objective (RPO &le; 1 minute).

## Consequences
Implementing this roadmap transitions the current take-home MVP into an enterprise-grade, audit-ready financial banking platform satisfying regulatory compliance without altering core business domain logic or calculation integrity.
