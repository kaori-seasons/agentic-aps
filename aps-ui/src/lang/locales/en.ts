const lang_en = {
  table: {
    fresh: 'refresh',
    freshCash: 'Refresh cache',
    keyword: 'Please enter keywords',
    add: 'new',
    plus: 'add',
    edit: 'edit',
    view: 'view',
    info: 'info',
    preview: 'preview',
    delete: 'delete',
    oprate: 'operation',
    enable: 'enable',
    disabled: 'disabled',
    import: 'import',
    remove: 'remove',
    unload: 'unload',
    deploy: 'deploy'
  },
  form: {
    submit: 'submit',
    search: 'search',
    save: 'save',
    reset: 'reset',
    cancel: 'cancel'
  },
  common: {
    all: 'all',
    search: 'search',
    message: 'message',
    submit: 'submit',
    confirm: 'confirm',
    cancel: 'cancel',
    explain: 'explain',
    tip: 'tips',
    tips: 'tips',
    success: 'success',
    fail: 'fail',
    pass: 'pass',
    nopass: 'Not pass',
    yes: 'yes',
    no: 'no',
    addSuccess: 'New successfully added',
    editSuccess: 'Edit successful',
    delSuccess: 'Delete successful',
    uploadSuccess: 'Upload successful',
    saveSuccess: 'Save successful',
    copySuccess: 'Copy successful',
    freshSuccess: 'Refresh successful',
    setSuccess: 'Set successfully',
    removeSuccess: 'Successfully removed',
    oprateSuccess: 'Operation successful',
    importSuccess: 'Import successful',
    deploySuccess: 'Successfully published',
    unloadSuccess: 'Uninstallation successful',
    sysSuccess: 'Sync successful',
    copyFail: 'Copy failed',
    devWarning: 'Developers should configure and use it during source code development. Non administrators are not allowed to modify it.',
    learnMore: 'Learn more',
    deleteConfirm: 'This operation will permanently delete this data. Do you want to continue?',
    all_dept: 'All departments',
    curr_dept: 'Current department',
    curr_dept_tree: 'Current department and below',
    all_job: 'All positions',
    curr_job: 'Current position',
    self: 'User creates data themselves',
    customize: 'Custom permissions',
    rightDrawer: {
      search: 'search',
      searchResult: 'Search results',
      emptyCon: 'No content yet',
      message: 'notification',
      notify: 'notify',
      allRead: 'All read',
      allMessage: 'Historical news',
      close: 'close',
      usercenter: 'Personal Center',
      accountInfo: 'Account settings',
      joinOrg: 'Join an organization',
      createOrg: 'Create an organization',
      accSet: 'Account settings',
      sigOut: 'Sign out',
      switchTenant: 'Switch tenant',
      accountSetting: {
        user: 'Personal information',
        userColumn: {
          userTitle: 'Personal Center',
          avatar: 'avatar',
          nickName: 'Nick name',
          rolePermision: 'Role Permissions',
          deptName: 'department',
          employeeNo: 'Employee number',
          level: 'grade',
          jobName: 'Position',
        },
        account: 'Account management',
        accountColumn: {
          accountTitle: 'Account management',
          bind: 'Bind account',
          bindPhone: 'Bind phone number',
          phonePlaceholder: 'Please enter your phone number',
          vcPlaceholder: 'Please enter the verification code',
          bindEmail: 'Bind email',
          emailPlaceholder: 'Please enter your email address',
          bindThird: 'Bind third-party accounts',
          WECHAT_MP: 'WeChat',
          WX_ENTERPRISE: 'WeCom',
          Ding: 'DingTalk',
          LDAP: 'LDAP'
        },
        password: 'Set password',
        passwordColumn: {
          passwordTitle: 'Set password',
          passText: 'Set login password',
          passPlaceholder: 'Please enter password',
          surePlaceholder: 'Confirm password'
        },
        button: {
          saveInfo: 'save',
          next: 'Next step',
          code: 'Get code',
          bind: 'bind',
          unbind: 'unbind',
          confirm: 'submit',
          cancel: 'cancel',
        },
        pleaseUse: 'Please use ',
        scanCode: ' scan the code',
        followOff: 'Follow official account',
        ldapColumn: {
          account: {
            label: 'Account number',
            placeholder: 'Please enter LDAP account'
          },
          password: {
            label: 'password',
            placeholder: 'Please enter LDAP password'
          }
        },
        imgValid: 'Only PNG, JPEG, JPG format images can be uploaded',
        unbPhone: 'We are about to unbind your phone number. Do you want to continue?',
        phone: 'phone',
        phoneSuccess: 'Successfully bound phone',
        unbEmail: 'The email binding is about to be unbound. Do you want to continue?',
        email: 'email',
        emailSuccess: 'Email binding successful',
        regain: 's regain',
        unbStrStart: 'About to be lifted ',
        unbStrEnd: ' binding, do you want to continue?',
        unbSuccess: 'Successfully unbound ',
        wxbindSuccess: 'WeChat binding successful',
        ldapSuccess: 'Successfully bound LDAP',
        passSuccess: 'Password set successfully',
        secondValid: 'The two passwords are inconsistent, please re-enter!',
      }
    },
    login: {
      tips: 'tips',
      outInfo: 'Do you want to exit the system or continue?',
      welLogin: 'Welcome to login',
      welReg: 'Register an account',
      passLogin: 'Password',
      codeLogin: 'Verification code',
      scanLogin: 'Scan Code Login',
      freshTip: 'The QR code has expired, click refresh',
      regTip: 'Clicking the registration button will be deemed as your agreement',
      terms: 'Terms of Use',
      goLogin: 'Go log in',
      otherType: 'Other methods',
      loginNow: 'Log in now',
      regNow: 'register',
      placeholder: {
        username: 'Please enter your account number',
        account: 'Please enter your LDAP account',
        phone: 'Please enter your phone number',
        password: 'Please enter password',
        code: 'Please enter the verification code',
        getcode: 'obtain',
        sendcode: 'send',
        resendcoed: 'resend',
        wechat: 'Please use WeChat scanning code to follow official account login',
        logging: 'Logging in...',
        ding: 'Please use DingTalk to scan and log in',
        wxenter: 'Please use Enterprise WeChat to scan and log in'
      }
    }
  },
  topNav: {
    appCenter: 'Application Center',
    modeUser: {
      GA: 'Formal mode',
      BETA: 'Test mode',
      DEV: 'Development mode',
      title: 'Switching modes',
      GA_tip: 'Real users use application functions',
      BETA_tip: 'Simulate different personnel using application testing permissions, etc',
      DEV_tip: 'Used for configuring and adding designs',
      user: 'Simulators (optional)',
      userPlaceholder: 'Please select a simulator'
    },
    system: 'Platform management',
    platform: 'Operation and maintenance settings',
    support: {
      title: 'Consulting support',
      desc: 'Edition price'
    }
  },
  localMenu: {
    // 基础菜单
    dataBase: 'Data Base',
    matter: 'matter',
    resource: 'Principal resource',
    productionOrder: 'Production order',
    materialPlan: 'To the material plan',
    bom: 'Make BOM',
    productionEngineering: 'Production engineering',
    technology: 'Process template',
    routing: 'routing',
    productionCalendar: 'Production calendar',
    programOfProduction: 'program of production',
    productionSchedulingStrategy: 'Production scheduling strategy',
    productionSchedulingPlan: 'Production scheduling plan',
  },
  aps: {
    dataBase: {
      matter: {
        add: 'Add new materials',
        edit: 'Modify material',
        success: ' success'
      },
      resource: {
        add: 'Add principal resource',
        edit: 'Modify principal resource',
        success: ' success'
      },
      productionOrder: {
        add: 'Add order',
        edit: 'Edit order',
        success: ' success',
        hasSupplement: 'replenish'
      },
      materialPlan: {
        add: 'Add incoming materials',
        import: 'Import resources',
        edit: 'Edit incoming materials',
        success: ' success'
      },
      bom: {
        title: 'BOM List',
        searchPlaceholder: 'Search for the item name / encoding',
        add: 'Add BOM',
        edit: 'Edit BOM'
      }
    },
    productionEngineering: {
      technology: {
        add: 'Add process',
        edit: 'Edit process',
        success: ' success'
      },
      routing: {
        leftTitle: 'Material list',
        add: 'New process route',
        edit: 'Modify the process route',
        success: ' success',
        enable: 'Start using',
        disable: 'Forbidden',
        choose: 'Select the process'
      },
      productionCalendar: {
        addMode: 'New Work Mode',
        editMode: 'Modify Work Mode',
        delMode: 'Delete the working mode',
        success: ' success',
        addTime: 'New time',
        add: 'New calendar',
        edit: 'Modify calendar'
      }
    },
    programOfProduction: {
      productionSchedulingStrategy: {
        add: 'Add strategy',
        edit: 'Modify the strategy',
        success: ' success',
        addOne: 'Add a line',
      },
      productionSchedulingPlan: {
        generate: 'Intelligent scheduling',
        success: ' success',
        genTitle: 'Select production scheduling strategy',
        abandon: 'abandon',
        orders: {
          move: 'sort',
          select: 'Select the target order',
        }
      }
    }
  }
}
export default lang_en