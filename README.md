# [OpenCompany](https://opencompany.com/) Email Service

[![MPL License](http://img.shields.io/badge/license-MPL-blue.svg?style=flat)](https://www.mozilla.org/MPL/2.0/)
[![Build Status](http://img.shields.io/travis/open-company/open-company-email.svg?style=flat)](https://travis-ci.org/open-company/open-company-email)
[![Dependency Status](https://www.versioneye.com/user/projects/55e5a34c8c0f62001b0003f3/badge.svg?style=flat)](https://www.versioneye.com/user/projects/55e5a34c8c0f62001b0003f3)
[![Roadmap on Trello](http://img.shields.io/badge/roadmap-trello-blue.svg?style=flat)](https://trello.com/b/3naVWHgZ/open-company-development)


## Background

> There is not a crime, there is not a dodge, there is not a trick, there is not a swindle, there is not a vice which does not live by secrecy.

> -- Joseph Pulitzer

Employees and investors, co-founders and execs, they all want more transparency from their startups, but there's no consensus about what it means to be transparent. OpenCompany is a platform that simplifies how key business information is shared with stakeholders.

When information about growth, finances, ownership and challenges is shared transparently, it inspires trust, new ideas and new levels of stakeholder engagement. OpenCompany makes it easy for founders to engage with employees and investors, creating a sense of ownership and urgency for everyone.

[OpenCompany](https://opencompany.com/) is GitHub for the rest of your company.

To maintain transparency, OpenCompany information is always accessible and easy to find. Being able to search or flip through prior updates empowers everyone. Historical context brings new employees and investors up to speed, refreshes memories, and shows how the company is evolving over time.

Transparency expectations are changing. Startups need to change as well if they are going to attract and retain savvy employees and investors. Just as open source changed the way we build software, transparency changes how we build successful startups with information that is open, interactive, and always accessible. The OpenCompany platform turns transparency into a competitive advantage.

Like the open companies we promote and support, the [OpenCompany](https://opencompany.com/) platform is completely transparent. The company supporting this effort, OpenCompany, LLC, is an open company. The [platform](https://github.com/open-company/open-company-web) is open source software, and open company data is [open data](https://en.wikipedia.org/wiki/Open_data) accessible through the [platform API](https://github.com/open-company/open-company-api).

To get started, head to: [OpenCompany](https://opencompany.com/)


## Overview

The OpenCompany Email Service handles composition and delivery of emails for other OpenCompany services.


## Local Setup

Users of the [OpenCompany](https://opencompany.com/) platform should get started by going to [OpenCompany](https://opencompany.com/). The following local setup is **for developers** wanting to work on the email software.

Most of the dependencies are internal, meaning [Leiningen](https://github.com/technomancy/leiningen) will handle getting them for you. There are a few exceptions:

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) - a Java 8+ JRE is needed to run Clojure
* [Leiningen](https://github.com/technomancy/leiningen) 2.5.1+ - Clojure's build and dependency management tool
* [Node.js](https://nodejs.org/en/) 4.4.5+ - JavaScript runtime
* [Juice](https://github.com/Automattic/juice) 2.0.0+ - Inlines CSS into HTML

#### Java

Chances are your system already has Java 8+ installed. You can verify this with:

```console
java -version
```

If you do not have Java 8+ [download it](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and follow the installation instructions.

#### Leiningen

Leiningen is easy to install:

1. Download the latest [lein script from the stable branch](https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein).
1. Place it somewhere that's on your $PATH (`env | grep PATH`). `/usr/local/bin` is a good choice if it is on your PATH.
1. Set it to be executable. `chmod 755 /usr/local/bin/lein`
1. Run it: `lein` This will finish the installation.

Then let Leiningen install the rest of the dependencies:

```console
git clone https://github.com/open-company/open-company-api.git
cd open-company-api
lein deps
```

#### Node.js

For Mac OS X, download the latest long-term support (LTS) `.pkg` installer from the [Node.js download page](https://nodejs.org/en/download/). Double click the package to run it.

For Linux, install nodejs with your distribution's preferred package manager, e.g. `sudo apt-get install build-essential nodejs`.

You can verify your Node.js installation by running:

```console
node -v
npm -v
```

#### Juice

The Node.js package manager, `npm`, can be used to install juice:

```console
npm install juice -g
```

You can verify your juice installation by running:

```console
juice -V
```

#### Required Secrets

An [AWS SQS queue](https://aws.amazon.com/sqs/) is used to pass messages from other OpenCompany services to the email service. Setup an SQS Queue and key/secret/endpoint access to the queue using the AWS Web Console or API.

Make sure you update the section in `project.clj` that looks like this to contain your actual JWT and AWS SQS secrets, and your email domain configuration:

```clojure
:dev [:qa {
  :env ^:replace {
    :aws-access-key-id "CHANGE-ME"
    :aws-secret-access-key "CHANGE-ME"
    :endpoint "us-east-1"
    :aws-sqs-email-queue "https://sqs.REGION.amazonaws.com/CHANGE/ME"
    :email-from-domain "change-me.com"
  }
```

You can also override these settings with environmental variables in the form of `AWS_ACCESS_KEY_ID`, etc. Use environmental variables to provide production secrets when running in production.


## Usage

TBD.


## Testing

Tests are run in continuous integration of the `master` and `mainline` branches on [Travis CI](https://travis-ci.org/open-company/open-company-api):

[![Build Status](http://img.shields.io/travis/open-company/open-company-email.svg?style=flat)](https://travis-ci.org/open-company/open-company-email)

To run the tests locally:

```console
lein test!
```


## Participation

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/open-company/open-company-api/blob/mainline/CODE-OF-CONDUCT.md). By participating in this project you agree to abide by its terms.


## License

Distributed under the [Mozilla Public License v2.0](http://www.mozilla.org/MPL/2.0/).

Copyright © 2016 OpenCompany, LLC.