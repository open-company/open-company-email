# Ensuring Mail Deliverability

SES (Simple Email Service) is Amazons email delivering service. Some configuration is needed before using SES
to avoid emails being flagged as spam.

## Deliverability Standards

3 standards are important for email deliverability:

**DKIM** - [DomainKeys Indentified Mail](https://en.wikipedia.org/wiki/DomainKeys_Identified_Mail)

Public key based email authentication to detect forged sender spoofing. Allows the server receiving the email to check
that the owner of the domain verified it.

Works by attaching a digital signature to the email, created by a private key known only to the owner of the domain,
which is then verified using a public key available from a DNS lookup on the domain.

**SPF** - [Sender Policy Framework](https://en.wikipedia.org/wiki/Sender_Policy_Framework)

Allows the server receiving the email to check that the email came from a server that the owner of the domain has
allowed to send email.

Works by looking up a record in the DNS records for the domain containing authorized email senders.

**DMARC** - [Domain-based Message Authentication, Reporting and Conformance](https://en.wikipedia.org/wiki/DMARC))

DMARC is an umbrella policy standard that uses DKIM and SPF. It allows the sender to specify a policy on DKIM and SPF
usage, what to do in the case of failures, and how failures should be reported.

## From and Mail From Headers

`FROM` is email address the user sees in their email client. `MAIL FROM` (aka envelope sender, envelope from, bounce
address, return path) is a domain that indicates the source of the message.

By default, SES uses a subdomain of amazonses.com as the `MAIL FROM` domain.

## Configuring DKIM and MAIL FROM with SES and Route 53

Follow the steps here to verify your domain, generate DKIM keys, and add them to DNS entries in Route 53:

[https://www.chrisanthropic.com/sending-mail-ses-route53-dkim-spf-dmarc/](Sending mail with AWS SES and Route 53)

Follow the steps in the same post to setup the `MAIL FROM` to a subdomain (`bounce` is a good one to use) rather
than have it be use SES as the `MAIL FROM`.

## Configuring SPF and DMARC with SES and Route 53

The raw DMARC reports are daily and not super easy to deal with. Better to have a nicely formatted report weekly.

Luckily, where there is a problem, there is a SaaS. Postmark offers a free DMARC aggregator.

First, signup here [https://dmarc.postmarkapp.com/](https://dmarc.postmarkapp.com/) specifying the **subdomain** you
used above as the `MAIL FROM` header (such as bounce.company.com).

Follow the directions provided during signup to add the DMARC entry TXT record to your DNS in Route 53.

Details on this step can be found here: [https://www.chrisanthropic.com/sending-mail-ses-route53-dkim-spf-dmarc/](Sending mail with AWS SES and Route 53)

After you setup the DNS record, click the verify button on Postmark's service and you'll get an email when it's able
to verify your DNS entry.

Give the DNS entries some time to propagate (few hours should be enough, could take up to 24 though), then verify
the DMARC settings at:

[https://dmarcian.com/dmarc-inspector/](https://dmarcian.com/dmarc-inspector/)

Enter the `MAIL FROM` subdomain you selected above and ensure it reports a valid DMARC record.

You may want to repeat this process for the top level domain as well if you don't have a DMARC record for it. Otherwise
Dmarcian will flag the TLD as not having a DMARC record and if you inspect mail headers you'll see in some cases the
DMARC is reported as `none` as the server looked in the TLD for it.

## Reference

[https://docs.aws.amazon.com/ses/latest/DeveloperGuide/mail-from.html](Using a Custom MAIL FROM Domain with Amazon SES)