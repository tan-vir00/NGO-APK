package com.ngoloanmanager

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.ngoloanmanager.data.*
import com.ngoloanmanager.domain.FinancialSummary
import com.ngoloanmanager.domain.InputValidation
import com.ngoloanmanager.domain.InterestPeriod
import com.ngoloanmanager.domain.LoanFinancials
import com.ngoloanmanager.domain.LoanMath
import com.ngoloanmanager.domain.PaymentFinancials
import com.ngoloanmanager.domain.PortfolioSummary
import com.ngoloanmanager.domain.UserFacingException
import com.ngoloanmanager.domain.userMessage
import com.ngoloanmanager.ui.components.*
import com.ngoloanmanager.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.security.SecureRandom
import java.time.LocalDate
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = LoanDatabase.get(this)
        setContent {
            NgoLoanTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LoanApp(db)
                }
            }
        }
    }
}

private fun hashPassword(password: String, salt: ByteArray): String {
    val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
    return Base64.encodeToString(
        javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
        Base64.NO_WRAP
    )
}

private fun money(poisha: Long): String =
    "৳" + BigDecimal.valueOf(poisha).divide(BigDecimal("100")).setScale(2).toPlainString()

private fun today() = LocalDate.now().toString()

/** Technical details go to logcat only; the UI always shows a Bangla message. */
private fun logFailure(what: String, e: Throwable) {
    android.util.Log.e("NGOLoan", "Failed: $what", e)
}

/** Dashboard/report totals derived from canonical loan + payment rows (see FinancialSummary). */
@Composable
private fun rememberPortfolioSummary(dao: LoanDao): PortfolioSummary {
    var loans by remember { mutableStateOf(emptyList<LoanEntity>()) }
    var payments by remember { mutableStateOf(emptyList<PaymentEntity>()) }
    LaunchedEffect(dao) {
        launch { dao.loans().collectLatest { loans = it } }
        launch { dao.allPayments().collectLatest { payments = it } }
    }
    return remember(loans, payments) {
        FinancialSummary.summarize(
            loans.map { LoanFinancials(it.id, it.principalPoisha, it.interestPoisha, it.totalPayablePoisha, it.status) },
            payments.map { PaymentFinancials(it.loanId, it.amountPoisha, it.cancelled) }
        )
    }
}

private enum class Page(val label: String, val icon: ImageVector) {
    DASHBOARD("হোম", Icons.Default.Home),
    BORROWERS("সদস্য", Icons.Default.People),
    LOANS("ঋণ", Icons.AutoMirrored.Filled.List),
    REPORTS("রিপোর্ট", Icons.Default.Assessment),
    SETTINGS("সেটিংস", Icons.Default.Settings)
}

@Composable
private fun LoanApp(db: LoanDatabase) {
    val dao = db.dao()
    val scope = rememberCoroutineScope()
    var authReady by remember { mutableStateOf<Boolean?>(null) }
    var loggedIn by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        authReady = dao.admin() != null
        setup = authReady == false
    }
    if (authReady == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    if (!loggedIn) {
        AuthScreen(
            firstRun = setup,
            error = error,
            onSubmit = { username, password ->
                scope.launch {
                    try {
                        val current = dao.admin()
                        if (current == null) {
                            if (password.length < 6) throw UserFacingException("পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে")
                            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                            dao.saveAdmin(
                                AdminEntity(
                                    username = username.trim().ifBlank { "admin" },
                                    salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                                    passwordHash = hashPassword(password, salt)
                                )
                            )
                            dao.audit(
                                AuditLogEntity(
                                    action = "ADMIN_SETUP",
                                    recordType = "admin",
                                    recordId = "1",
                                    details = "Initial administrator created"
                                )
                            )
                            setup = false
                            loggedIn = true
                            error = ""
                        } else {
                            val salt = Base64.decode(current.salt, Base64.NO_WRAP)
                            if (hashPassword(password, salt) == current.passwordHash) {
                                loggedIn = true
                                error = ""
                            } else error = "পাসওয়ার্ড সঠিক নয়"
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logFailure("auth", e)
                        error = e.userMessage("কাজটি সম্পন্ন করা যায়নি")
                    }
                }
            }
        )
        return
    }
    MainShell(db = db, onLogout = { loggedIn = false })
}

@Composable
private fun AuthScreen(firstRun: Boolean, error: String, onSubmit: (String, String) -> Unit) {
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
            elevation = 8.dp
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(EmeraldDeep, EmeraldPrimary))),
                contentAlignment = Alignment.Center
            ) {
                Text("৳", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "NGO Loan Manager",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (firstRun) "আপনার প্রতিষ্ঠানের অ্যাডমিন অ্যাকাউন্ট তৈরি করুন"
                else "আপনার অ্যাকাউন্টে লগইন করুন",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            PremiumTextField(username, { username = it }, "অ্যাডমিন ইউজারনেম")
            Spacer(Modifier.height(12.dp))
            PremiumTextField(
                password,
                { password = it },
                if (firstRun) "পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)" else "পাসওয়ার্ড",
                visualTransformation = PasswordVisualTransformation()
            )
            AnimatedVisibility(visible = error.isNotBlank()) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            error,
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            PremiumPrimaryButton(
                text = if (firstRun) "অ্যাকাউন্ট তৈরি করুন" else "লগইন",
                onClick = { onSubmit(username, password) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "অফলাইন · স্থানীয় ডেটা · নিরাপদ হ্যাশ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(db: LoanDatabase, onLogout: () -> Unit) {
    val dao = db.dao()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.DASHBOARD) }
    var borrowers by remember { mutableStateOf(emptyList<BorrowerEntity>()) }
    var loans by remember { mutableStateOf(emptyList<LoanEntity>()) }
    var borrowerCount by remember { mutableIntStateOf(0) }
    var activeCount by remember { mutableIntStateOf(0) }
    val summary = rememberPortfolioSummary(dao)
    var search by remember { mutableStateOf("") }
    var borrowerSaving by remember { mutableStateOf(false) }
    var borrowerError by remember { mutableStateOf("") }
    var loanSaving by remember { mutableStateOf(false) }
    var loanError by remember { mutableStateOf("") }
    var showBorrowerForm by remember { mutableStateOf(false) }
    var showLoanForm by remember { mutableStateOf(false) }
    var selectedBorrower by remember { mutableStateOf<BorrowerEntity?>(null) }
    var selectedLoan by remember { mutableStateOf<LoanEntity?>(null) }
    var ngoName by remember { mutableStateOf("NGO") }

    LaunchedEffect(Unit) {
        launch { dao.borrowers().collectLatest { borrowers = it } }
        launch { dao.loans().collectLatest { loans = it } }
        launch { dao.borrowerCount().collectLatest { borrowerCount = it } }
        launch { dao.activeLoanCount().collectLatest { activeCount = it } }
        ngoName = dao.setting("ngo_name") ?: "NGO"
    }

    if (selectedLoan != null) {
        LoanDetailScreen(db, selectedLoan!!, onBack = { selectedLoan = null })
        return
    }
    if (selectedBorrower != null) {
        BorrowerDetailScreen(
            db,
            selectedBorrower!!,
            onBack = { selectedBorrower = null },
            onCreateLoan = {
                showLoanForm = true
                page = Page.LOANS
                selectedBorrower = null
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            ngoName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "অফলাইন ঋণ ব্যবস্থাপনা",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("লগআউট", style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Page.entries.forEach { p ->
                    NavigationBarItem(
                        selected = page == p,
                        onClick = { page = p },
                        icon = { Icon(p.icon, contentDescription = p.label) },
                        label = { Text(p.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (page) {
                Page.DASHBOARD -> DashboardScreen(
                    ngoName = ngoName,
                    borrowerCount = borrowerCount,
                    activeCount = activeCount,
                    summary = summary,
                    loans = loans,
                    onAddMember = { showBorrowerForm = true },
                    onCreateLoan = { showLoanForm = true },
                    onOpenLoan = { selectedLoan = it }
                )
                Page.BORROWERS -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PremiumTextField(
                                search,
                                { search = it },
                                "নাম / ফোন / সদস্য ID খুঁজুন",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            PremiumPrimaryButton("+ সদস্য", { showBorrowerForm = true }, Modifier.widthIn(min = 100.dp))
                        }
                        val filtered = borrowers.filter {
                            search.isBlank() ||
                                it.name.contains(search, true) ||
                                it.phone.contains(search, true) ||
                                it.memberCode.contains(search, true)
                        }
                        if (filtered.isEmpty()) {
                            EmptyStateView("কোনো সদস্য পাওয়া যায়নি। নতুন সদস্য যোগ করুন।", "👤")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered, key = { it.id }) { b ->
                                    ListRowCard(
                                        title = b.name,
                                        subtitle = "${b.memberCode} · ${b.phone}",
                                        meta = b.address.takeIf { it.isNotBlank() },
                                        onClick = { selectedBorrower = b },
                                        leading = {
                                            Box(
                                                Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    b.name.take(1).uppercase(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Page.LOANS -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "ঋণের তালিকা",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            PremiumPrimaryButton("+ নতুন ঋণ", { showLoanForm = true })
                        }
                        if (loans.isEmpty()) {
                            EmptyStateView("এখনও কোনো ঋণ তৈরি হয়নি। প্রথমে সদস্য যোগ করুন।", "৳")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(loans, key = { it.id }) { loan ->
                                    val borrower = borrowers.find { it.id == loan.borrowerId }
                                    ListRowCard(
                                        title = "${loan.loanCode} · ${borrower?.name ?: "সদস্য #${loan.borrowerId}"}",
                                        subtitle = "ঋণ ${money(loan.principalPoisha)} · মোট পাওনা ${money(loan.totalPayablePoisha)}",
                                        meta = "${loan.termMonths} মাস",
                                        status = loan.status,
                                        statusActive = loan.status == "ACTIVE",
                                        onClick = { selectedLoan = loan }
                                    )
                                }
                            }
                        }
                    }
                }
                Page.REPORTS -> ReportsScreen(db)
                Page.SETTINGS -> SettingsScreen(db, onLogout)
            }
        }
    }

    if (showBorrowerForm) {
        BorrowerFormDialog(
            saving = borrowerSaving,
            error = borrowerError,
            onDismiss = { showBorrowerForm = false; borrowerError = "" },
            onSave = { name, phone, address, nid, notes ->
                if (!borrowerSaving) {
                    borrowerSaving = true
                    borrowerError = ""
                    scope.launch {
                        try {
                            val code = "MBR-" + UUID.randomUUID().toString().take(8).uppercase()
                            db.withTransaction {
                                val id = dao.insertBorrower(
                                    BorrowerEntity(
                                        memberCode = code,
                                        name = name.trim(),
                                        phone = phone.trim(),
                                        address = address.trim(),
                                        nationalId = nid.trim(),
                                        registeredAt = today(),
                                        notes = notes.trim()
                                    )
                                )
                                dao.audit(
                                    AuditLogEntity(
                                        action = "BORROWER_CREATED",
                                        recordType = "borrower",
                                        recordId = id.toString(),
                                        details = "Member $code created"
                                    )
                                )
                            }
                            showBorrowerForm = false
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logFailure("create borrower", e)
                            borrowerError = e.userMessage("সদস্য সংরক্ষণ করা যায়নি। আবার চেষ্টা করুন।")
                        } finally {
                            borrowerSaving = false
                        }
                    }
                }
            }
        )
    }
    if (showLoanForm) {
        LoanFormDialog(
            borrowers = borrowers,
            saving = loanSaving,
            error = loanError,
            onDismiss = { showLoanForm = false; loanError = "" },
            onSave = { borrowerId, principalP, rate, period, term, loanDate, firstDue, notes ->
                if (!loanSaving) {
                    loanSaving = true
                    loanError = ""
                    scope.launch {
                        try {
                            val calc = LoanMath.calculate(principalP, InputValidation.rate(rate), term, period)
                            val code = "LN-" + UUID.randomUUID().toString().take(8).uppercase()
                            db.withTransaction {
                                val id = dao.insertLoan(
                                    LoanEntity(
                                        loanCode = code,
                                        borrowerId = borrowerId,
                                        principalPoisha = principalP,
                                        ratePercent = rate,
                                        interestPeriod = period.name,
                                        termMonths = term,
                                        loanDate = loanDate,
                                        firstDueDate = firstDue,
                                        interestPoisha = calc.interestPoisha,
                                        totalPayablePoisha = calc.totalPayablePoisha,
                                        notes = notes
                                    )
                                )
                                val dates = LoanMath.monthlyDueDates(LocalDate.parse(firstDue), term)
                                dao.insertInstallments(
                                    dates.mapIndexed { i, date ->
                                        InstallmentEntity(
                                            loanId = id,
                                            number = i + 1,
                                            dueDate = date.toString(),
                                            dueAmountPoisha = calc.installmentsPoisha[i]
                                        )
                                    }
                                )
                                dao.audit(
                                    AuditLogEntity(
                                        action = "LOAN_CREATED",
                                        recordType = "loan",
                                        recordId = id.toString(),
                                        details = "Created $code for borrower $borrowerId"
                                    )
                                )
                            }
                            showLoanForm = false
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: ArithmeticException) {
                            logFailure("create loan (arithmetic)", e)
                            loanError = "পরিমাণ বা হার অনেক বড়। সঠিক মান দিন।"
                        } catch (e: Exception) {
                            logFailure("create loan", e)
                            loanError = e.userMessage("ঋণ তৈরি করা যায়নি। ফর্মের তথ্য যাচাই করে আবার চেষ্টা করুন।")
                        } finally {
                            loanSaving = false
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun DashboardScreen(
    ngoName: String,
    borrowerCount: Int,
    activeCount: Int,
    summary: PortfolioSummary,
    loans: List<LoanEntity>,
    onAddMember: () -> Unit,
    onCreateLoan: () -> Unit,
    onOpenLoan: (LoanEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeroCard(
                title = "স্বাগতম, $ngoName",
                subtitle = "আজকের সারাংশ · ${today()}"
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("৳", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("মোট সদস্য", "$borrowerCount", "👤", Modifier.weight(1f))
                MetricCard("সক্রিয় ঋণ", "$activeCount", "📋", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("মোট মূল ঋণ", money(summary.principalPoisha), "৳", Modifier.weight(1f), EmeraldPrimary)
                MetricCard("মোট আদায়", money(summary.collectedPoisha), "↓", Modifier.weight(1f), SuccessGreen)
            }
        }
        item {
            GlassCard(elevation = 3.dp) {
                Text("মোট বকেয়া (সুদসহ)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(money(summary.outstandingPoisha), style = MoneyStyleLarge, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "মোট পাওনা ${money(summary.totalPayablePoisha)} · অগ্রিম জমা ${money(summary.advancePoisha)} (বাতিল ঋণ ও বাতিল পেমেন্ট বাদে)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SectionHeader("দ্রুত কাজ")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionChip("সদস্য যোগ", onAddMember)
                QuickActionChip("নতুন ঋণ", onCreateLoan)
            }
        }
        item {
            SectionHeader("সাম্প্রতিক ঋণ", "সর্বশেষ তৈরি করা ঋণগুলো")
        }
        if (loans.isEmpty()) {
            item {
                GlassCard { EmptyStateView("এখনও কোনো ঋণ তৈরি হয়নি। প্রথমে সদস্য যোগ করুন, তারপর নতুন ঋণ তৈরি করুন।") }
            }
        } else {
            items(loans.take(8), key = { it.id }) { l ->
                ListRowCard(
                    title = l.loanCode,
                    subtitle = "মোট পাওনা ${money(l.totalPayablePoisha)}",
                    status = l.status,
                    statusActive = l.status == "ACTIVE",
                    onClick = { onOpenLoan(l) },
                    leading = {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("৳", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
        item {
            Text(
                "হিসাব যাচাইয়ের নোট: advance allocation ও বাতিল ঋণের সমন্বয়ের জন্য ঋণের বিস্তারিত দেখুন।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BorrowerFormDialog(
    saving: Boolean,
    error: String,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String, nid: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var nid by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    val shownError = localError.ifBlank { error }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        shape = DialogShape,
        title = { Text("নতুন সদস্য", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumTextField(name, { name = it }, "নাম *")
                PremiumTextField(phone, { phone = it }, "ফোন")
                PremiumTextField(address, { address = it }, "ঠিকানা")
                PremiumTextField(nid, { nid = it }, "জাতীয় পরিচয়পত্র")
                PremiumTextField(notes, { notes = it }, "নোট", singleLine = false)
                if (shownError.isNotBlank()) {
                    Text(shownError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            PremiumPrimaryButton("সংরক্ষণ", {
                if (name.isBlank()) localError = "সদস্যের নাম লিখুন"
                else {
                    localError = ""
                    onSave(name, phone, address, nid, notes)
                }
            }, loading = saving)
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("বাতিল") }
        }
    )
}

@Composable
private fun LoanFormDialog(
    borrowers: List<BorrowerEntity>,
    saving: Boolean,
    error: String,
    onDismiss: () -> Unit,
    onSave: (borrowerId: Long, principalPoisha: Long, rate: String, period: InterestPeriod, term: Int, loanDate: String, firstDue: String, notes: String) -> Unit
) {
    var borrowerId by remember { mutableLongStateOf(borrowers.firstOrNull()?.id ?: 0) }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("12") }
    var term by remember { mutableStateOf("10") }
    var period by remember { mutableStateOf(InterestPeriod.ANNUAL) }
    var loanDate by remember { mutableStateOf(today()) }
    var firstDue by remember { mutableStateOf(today()) }
    var notes by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    val shownError = localError.ifBlank { error }
    val preview = try {
        val p = BigDecimal(principal.ifBlank { "0" }).multiply(BigDecimal("100")).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        val t = term.toInt()
        if (p > 0 && t > 0) LoanMath.calculate(p, BigDecimal(rate), t, period) else null
    } catch (e: Exception) { null }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        shape = DialogShape,
        title = { Text("নতুন ঋণ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (borrowers.isEmpty()) {
                    Text("আগে সদস্য যোগ করুন", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("সদস্য", style = MaterialTheme.typography.labelMedium)
                    borrowers.take(20).forEach { b ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (borrowerId == b.id) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = borrowerId == b.id, onClick = { borrowerId = b.id })
                            Text("${b.name} (${b.memberCode})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                PremiumTextField(principal, { principal = it }, "মূল ঋণ (টাকা)")
                PremiumTextField(rate, { rate = it }, "সুদের হার (%)")
                PremiumTextField(term, { term = it }, "মেয়াদ (মাস)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("সুদের ধরন:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = period == InterestPeriod.ANNUAL,
                        onClick = { period = InterestPeriod.ANNUAL },
                        label = { Text("বার্ষিক") }
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = period == InterestPeriod.FULL_TERM,
                        onClick = { period = InterestPeriod.FULL_TERM },
                        label = { Text("পূর্ণ মেয়াদ") }
                    )
                }
                PremiumTextField(loanDate, { loanDate = it }, "ঋণের তারিখ (YYYY-MM-DD)")
                PremiumTextField(firstDue, { firstDue = it }, "প্রথম কিস্তির তারিখ")
                PremiumTextField(notes, { notes = it }, "নোট", singleLine = false)
                if (preview != null) {
                    GlassCard(elevation = 0.dp) {
                        Text("সুদ: ${money(preview.interestPoisha)}", style = MoneyStyleSmall, color = MaterialTheme.colorScheme.primary)
                        Text("মোট পাওনা: ${money(preview.totalPayablePoisha)}", style = MoneyStyleSmall)
                    }
                }
                if (shownError.isNotBlank()) {
                    Text(shownError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            PremiumPrimaryButton("ঋণ তৈরি", {
                localError = ""
                try {
                    if (borrowers.isEmpty()) throw UserFacingException("আগে সদস্য যোগ করুন")
                    if (borrowerId <= 0) throw UserFacingException("সদস্য নির্বাচন করুন")
                    val p = InputValidation.takaToPoisha(principal, "মূল ঋণ")
                    val r = InputValidation.rate(rate)
                    val tm = InputValidation.termMonths(term)
                    val ld = InputValidation.date(loanDate, "ঋণের তারিখ")
                    val fd = InputValidation.date(firstDue, "প্রথম কিস্তির তারিখ")
                    if (fd.isBefore(ld)) throw UserFacingException("প্রথম কিস্তির তারিখ ঋণের তারিখের আগে হতে পারে না")
                    onSave(borrowerId, p, r.toPlainString(), period, tm, ld.toString(), fd.toString(), notes.trim())
                } catch (e: UserFacingException) {
                    localError = e.message ?: "ইনপুট যাচাই করুন"
                }
            }, loading = saving)
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("বাতিল") }
        }
    )
}

@Composable
private fun BorrowerDetailScreen(
    db: LoanDatabase,
    borrower: BorrowerEntity,
    onBack: () -> Unit,
    onCreateLoan: () -> Unit
) {
    val dao = db.dao()
    var loans by remember { mutableStateOf(emptyList<LoanEntity>()) }
    LaunchedEffect(borrower.id) {
        dao.loansForBorrower(borrower.id).collectLatest { loans = it }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("ফিরে যান")
        }
        GlassCard(elevation = 3.dp) {
            Text(borrower.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(borrower.memberCode, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (borrower.phone.isNotBlank()) Text("ফোন: ${borrower.phone}", style = MaterialTheme.typography.bodyMedium)
            if (borrower.address.isNotBlank()) Text("ঠিকানা: ${borrower.address}", style = MaterialTheme.typography.bodyMedium)
            if (borrower.nationalId.isNotBlank()) Text("NID: ${borrower.nationalId}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            PremiumPrimaryButton("নতুন ঋণ তৈরি", onCreateLoan, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        Text("এই সদস্যের ঋণ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (loans.isEmpty()) {
            EmptyStateView("এই সদস্যের কোনো ঋণ নেই")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(loans, key = { it.id }) { loan ->
                    GlassCard {
                        Text(loan.loanCode, fontWeight = FontWeight.SemiBold)
                        Text("মূল ${money(loan.principalPoisha)} · মোট ${money(loan.totalPayablePoisha)}")
                        StatusChip(loan.status, loan.status == "ACTIVE")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanDetailScreen(db: LoanDatabase, loan: LoanEntity, onBack: () -> Unit) {
    val dao = db.dao()
    val scope = rememberCoroutineScope()
    var installments by remember { mutableStateOf(emptyList<InstallmentEntity>()) }
    var payments by remember { mutableStateOf(emptyList<PaymentEntity>()) }
    var borrower by remember { mutableStateOf<BorrowerEntity?>(null) }
    var amount by remember { mutableStateOf("") }
    var payDate by remember { mutableStateOf(today()) }
    var notes by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var messageOk by remember { mutableStateOf(true) }
    var cancelTarget by remember { mutableStateOf<PaymentEntity?>(null) }
    var cancelReason by remember { mutableStateOf("") }
    var cancelError by remember { mutableStateOf("") }
    var cancelling by remember { mutableStateOf(false) }
    val repo = remember(db) { PaymentRepository(db) }

    LaunchedEffect(loan.id) {
        borrower = dao.borrower(loan.borrowerId)
        launch { dao.installments(loan.id).collectLatest { installments = it } }
        launch { dao.payments(loan.id).collectLatest { payments = it } }
    }

    val paid = payments.filterNot { it.cancelled }.sumOf { it.amountPoisha }
    val advance = FinancialSummary.advance(loan.totalPayablePoisha, paid)
    val outstanding = FinancialSummary.outstanding(loan.totalPayablePoisha, paid)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("ঋণের তালিকা")
        }
        Text(loan.loanCode, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            borrower?.name ?: "সদস্য #${loan.borrowerId}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        GlassCard(elevation = 3.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("মূল ঋণ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(loan.principalPoisha), style = MoneyStyleSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("সুদ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(loan.interestPoisha), style = MoneyStyleSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${loan.ratePercent}% · ${loan.interestPeriod} · ${loan.termMonths} মাস",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("মোট পাওনা", style = MaterialTheme.typography.labelSmall)
                    Text(money(loan.totalPayablePoisha), style = MoneyStyle, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("আদায়", style = MaterialTheme.typography.labelSmall)
                    Text(money(paid), style = MoneyStyleSmall, color = SuccessGreen)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("বকেয়া: ${money(outstanding)}", style = MoneyStyleSmall, color = MaterialTheme.colorScheme.secondary)
                Text("Advance: ${money(advance)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("কিস্তির সময়সূচি", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        installments.forEach { inst ->
            val remaining = (inst.dueAmountPoisha - inst.allocatedPoisha).coerceAtLeast(0)
            val statusLabel = when {
                remaining == 0L -> "পরিশোধিত"
                inst.allocatedPoisha > 0 -> "আংশিক"
                inst.dueDate < today() -> "বকেয়া"
                else -> "অপরিশোধিত"
            }
            GlassCard(elevation = 1.dp, modifier = Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("কিস্তি ${inst.number} · ${inst.dueDate}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "নির্ধারিত ${money(inst.dueAmountPoisha)} · বাকি ${money(remaining)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusChip(statusLabel, remaining == 0L)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("পেমেন্ট জমা", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        GlassCard(elevation = 2.dp) {
            PremiumTextField(amount, { amount = it }, "পরিমাণ (টাকা)")
            Spacer(Modifier.height(10.dp))
            PremiumTextField(payDate, { payDate = it }, "তারিখ (YYYY-MM-DD)")
            Spacer(Modifier.height(10.dp))
            PremiumTextField(notes, { notes = it }, "নোট")
            Spacer(Modifier.height(16.dp))
            PremiumPrimaryButton(
                text = "পেমেন্ট সংরক্ষণ",
                loading = saving,
                onClick = {
                    if (!saving) {
                        saving = true
                        message = ""
                        scope.launch {
                            try {
                                val poisha = InputValidation.takaToPoisha(amount, "পেমেন্টের পরিমাণ")
                                repo.recordPayment(loan.id, poisha, payDate, notes)
                                amount = ""
                                notes = ""
                                messageOk = true
                                message = "পেমেন্ট সফলভাবে সংরক্ষিত হয়েছে"
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logFailure("record payment", e)
                                messageOk = false
                                message = e.userMessage("পেমেন্ট সংরক্ষণ করা যায়নি। আবার চেষ্টা করুন।")
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(visible = message.isNotBlank()) {
                Text(
                    message,
                    Modifier.padding(top = 10.dp),
                    color = if (messageOk) SuccessGreen else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { showHistory = !showHistory }) {
            Text(if (showHistory) "পেমেন্ট ইতিহাস লুকান" else "পেমেন্ট ইতিহাস দেখুন")
        }
        AnimatedVisibility(visible = showHistory, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
            Column {
                payments.forEach { p ->
                    GlassCard(elevation = 1.dp, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(p.receiptNo, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${p.paymentDate} · ${money(p.amountPoisha)}${if (p.cancelled) " · বাতিল" else ""}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!p.cancelled) {
                            TextButton(
                                onClick = { cancelReason = ""; cancelError = ""; cancelTarget = p },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("পেমেন্ট বাতিল")
                            }
                        }
                    }
                }
            }
        }
        cancelTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { if (!cancelling) cancelTarget = null },
                shape = DialogShape,
                title = { Text("পেমেন্ট বাতিল করবেন?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("ঋণ: ${loan.loanCode}", style = MaterialTheme.typography.bodyMedium)
                        Text("সদস্য: ${borrower?.name ?: "#${loan.borrowerId}"}", style = MaterialTheme.typography.bodyMedium)
                        Text("রসিদ: ${target.receiptNo} · ${target.paymentDate}", style = MaterialTheme.typography.bodyMedium)
                        Text("পরিমাণ: ${money(target.amountPoisha)}", style = MoneyStyleSmall)
                        Text(
                            "বাতিল করলে এই পেমেন্ট হিসাব থেকে বাদ যাবে এবং বাকি বৈধ পেমেন্টগুলো তারিখ অনুযায়ী পুরনো কিস্তি আগে নিয়মে পুনরায় বণ্টিত হবে। মূল রসিদ ও অডিট লগ সংরক্ষিত থাকবে। এই কাজ ফেরানো যাবে না।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PremiumTextField(cancelReason, { cancelReason = it }, "বাতিলের কারণ *")
                        if (cancelError.isNotBlank()) {
                            Text(cancelError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    PremiumPrimaryButton("বাতিল নিশ্চিত করুন", {
                        if (!cancelling) {
                            cancelling = true
                            cancelError = ""
                            scope.launch {
                                try {
                                    repo.cancelPayment(target.id, cancelReason)
                                    cancelTarget = null
                                    cancelReason = ""
                                    messageOk = true
                                    message = "পেমেন্ট সফলভাবে বাতিল হয়েছে"
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logFailure("cancel payment", e)
                                    cancelError = e.userMessage("পেমেন্ট বাতিল করা যায়নি। আবার চেষ্টা করুন।")
                                } finally {
                                    cancelling = false
                                }
                            }
                        }
                    }, loading = cancelling)
                },
                dismissButton = {
                    TextButton(onClick = { cancelTarget = null }, enabled = !cancelling) { Text("ফিরে যান") }
                }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReportsScreen(db: LoanDatabase) {
    val dao = db.dao()
    val summary = rememberPortfolioSummary(dao)
    var active by remember { mutableIntStateOf(0) }
    var members by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        launch { dao.activeLoanCount().collectLatest { active = it } }
        launch { dao.borrowerCount().collectLatest { members = it } }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("রিপোর্ট", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "সারাংশ পরিসংখ্যান · বিস্তারিত CSV/PDF এক্সপোর্ট এখনও উপলব্ধ নয়",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("সদস্য", "$members", "👤", Modifier.weight(1f))
            MetricCard("সক্রিয় ঋণ", "$active", "📋", Modifier.weight(1f))
        }
        MetricCard("মোট মূল ঋণ", money(summary.principalPoisha), "৳")
        MetricCard("মোট সুদ", money(summary.interestPoisha), "%")
        MetricCard("মোট পাওনা (মূল + সুদ)", money(summary.totalPayablePoisha), "৳")
        MetricCard("মোট আদায়", money(summary.collectedPoisha), "↓", accent = SuccessGreen)
        MetricCard("মোট বকেয়া", money(summary.outstandingPoisha), "!", accent = GoldWarm)
        MetricCard("অগ্রিম জমা", money(summary.advancePoisha), "+", accent = SuccessGreen)
        GlassCard {
            Text(
                "প্রতিটি ঋণের সঠিক বকেয়া, advance এবং বাতিল পেমেন্ট ঋণের বিস্তারিত পাতায় যাচাই করুন।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsScreen(db: LoanDatabase, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ngoName by remember { mutableStateOf("") }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { ngoName = db.dao().setting("ngo_name") ?: "NGO" }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("সেটিংস", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        GlassCard(elevation = 2.dp) {
            Text("প্রতিষ্ঠানের পরিচয়", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            PremiumTextField(ngoName, { ngoName = it }, "NGO-এর নাম")
            Spacer(Modifier.height(12.dp))
            PremiumPrimaryButton(
                "সেটিংস সংরক্ষণ",
                {
                    scope.launch {
                        db.dao().setSetting(AppSettingEntity("ngo_name", ngoName))
                        db.dao().audit(
                            AuditLogEntity(
                                action = "SETTING_CHANGED",
                                recordType = "setting",
                                recordId = "ngo_name",
                                details = "NGO name updated"
                            )
                        )
                        message = "সেটিংস সংরক্ষিত"
                    }
                },
                Modifier.fillMaxWidth()
            )
        }
        GlassCard(elevation = 2.dp) {
            Text("Admin password পরিবর্তন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            PremiumTextField(oldPassword, { oldPassword = it }, "বর্তমান পাসওয়ার্ড", visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(10.dp))
            PremiumTextField(newPassword, { newPassword = it }, "নতুন পাসওয়ার্ড (৬+ অক্ষর)", visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(12.dp))
            PremiumPrimaryButton(
                "পাসওয়ার্ড পরিবর্তন",
                {
                    scope.launch {
                        val admin = db.dao().admin()
                        if (admin == null || hashPassword(oldPassword, Base64.decode(admin.salt, Base64.NO_WRAP)) != admin.passwordHash) {
                            message = "বর্তমান পাসওয়ার্ড সঠিক নয়"
                        } else if (newPassword.length < 6) {
                            message = "নতুন পাসওয়ার্ড কমপক্ষে ৬ অক্ষর হতে হবে"
                        } else {
                            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                            db.dao().saveAdmin(
                                admin.copy(
                                    salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                                    passwordHash = hashPassword(newPassword, salt)
                                )
                            )
                            db.dao().audit(
                                AuditLogEntity(
                                    action = "PASSWORD_CHANGED",
                                    recordType = "admin",
                                    recordId = "1",
                                    details = "Admin password changed"
                                )
                            )
                            oldPassword = ""
                            newPassword = ""
                            message = "পাসওয়ার্ড পরিবর্তিত হয়েছে"
                        }
                    }
                },
                Modifier.fillMaxWidth()
            )
        }
        AnimatedVisibility(visible = message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        GlassCard {
            Text("ডেটা ব্যাকআপ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "এই প্রাথমিক সংস্করণে পূর্ণ encrypted backup/restore UI এখনও বাস্তবায়িত নয়। বাস্তব আর্থিক ডেটা ব্যবহারের আগে backup/restore সম্পন্ন ও যাচাই করতে হবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        PremiumOutlinedButton("লগআউট", onLogout, Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}
