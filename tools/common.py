#!/usr/bin/python3

# Copyright (C) 2021 Julian Valentin, lsp-cli Development Community
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

import re
import subprocess
from typing import Tuple



def getGitHubOrganizationRepository() -> Tuple[str, str]:
  output = subprocess.run(["git", "remote", "get-url", "origin"],
      stdout=subprocess.PIPE).stdout.decode()
  regexMatch = re.search(r"github.com[:/](.*?)/(.*?)(?:\.git)?$", output)
  assert regexMatch is not None, output
  organization, repository = regexMatch.group(1), regexMatch.group(2)
  return organization, repository

organization, repository = getGitHubOrganizationRepository()
