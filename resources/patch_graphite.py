import sys
import imp
import os

def make_module(name, **kwargs):
  mod = imp.new_module(name)
  mod.__dict__.update(kwargs)
  sys.modules[name] = mod

import graphite
make_module('django')

class Settings(object):
  pass
settings = Settings()
settings.TIME_ZONE = 'UTC'
settings.CARBONLINK_HOSTS = []
settings.CARBONLINK_TIMEOUT = -1
make_module('django.conf', settings=settings)

make_module('graphite.logger', log=lambda *x: None)
make_module('graphite.events', models=None)
make_module('graphite.render.glyph', format_units=None)
make_module('graphite.storage', STORE=None, LOCAL_STORE=None)
make_module('graphite.readers', FetchInProgress=None)

#from django.core.exceptions import ObjectDoesNotExist
make_module('django.core')
make_module('django.core.exceptions', ObjectDoesNotExist=None)

#from django.contrib.auth.models import User
make_module('django.contrib')
make_module('django.contrib.auth')
make_module('django.contrib.auth.models', User=None)

#from graphite.account.models import Profile
make_module('graphite.account')
make_module('graphite.account.models', Profile=None)
# from graphite.render.evaluator import evaluateTarget
make_module('graphite.render.evaluator', evaluateTarget=lambda *x: None)
make_module('pyparsing')
make_module('pytz', timezone=lambda *x: None)
os.environ['READTHEDOCS'] = 'true'
try:
  import graphite.util
except:
  pass
del os.environ['READTHEDOCS']
from graphite.render import functions
